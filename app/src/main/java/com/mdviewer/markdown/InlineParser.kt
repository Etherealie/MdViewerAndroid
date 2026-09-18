package com.mdviewer.markdown

/**
 * 行内解析器：把一行 Markdown 变成「纯文本 + 样式区间」。
 *
 * 支持：
 *   **粗体** / __粗体__ / *斜体* / _斜体_ / ~~删除线~~ / `行内代码`
 *   [文字](url) / ![图](url) / <https://a.com> / 裸链接 https://a.com
 *   \* 反斜杠转义
 *
 * 设计要点：一边扫描一边往输出里追加字符，所以样式区间的下标是**输出文本**的下标，
 * 渲染层直接用这些下标给 AnnotatedString 加 SpanStyle 即可。
 */
object InlineParser {

    fun parse(source: String): InlineText {
        val out = StringBuilder(source.length)
        val spans = ArrayList<InlineSpan>()
        emit(source, out, spans, emptySet())
        return InlineText(out.toString(), spans)
    }

    /** 纯文本快捷方式：不关心样式时用 */
    fun plain(source: String): String = parse(source).text

    // ------------------------------------------------------------------

    private fun emit(
        src: String,
        out: StringBuilder,
        spans: MutableList<InlineSpan>,
        styles: Set<InlineStyle>,
    ) {
        var i = 0
        while (i < src.length) {
            val c = src[i]

            // 1) 反斜杠转义
            if (c == '\\' && i + 1 < src.length) {
                add(out, spans, src[i + 1].toString(), styles)
                i += 2
                continue
            }

            // 2) 行内代码：最高优先级，里面的 * _ 等符号不再解析
            if (c == '`') {
                val fence = "`".repeat(runLength(src, i, '`'))
                val close = src.indexOf(fence, i + fence.length)
                if (close > i) {
                    val start = out.length
                    out.append(src.substring(i + fence.length, close).trim())
                    spans.add(InlineSpan(start, out.length, InlineStyle.CODE))
                    i = close + fence.length
                    continue
                }
            }

            // 2.5) 数学公式：$...$ 或 $$...$$
            if (c == '$') {
                val display = src.startsWith("$$", i)
                val fence = if (display) "$$" else "$"
                val bodyStart = i + fence.length
                val openOk = bodyStart < src.length && !src[bodyStart].isWhitespace()
                val close = if (openOk) findMathClose(src, bodyStart, fence) else -1
                if (close > bodyStart) {
                    val start = out.length
                    val math = MathRenderer.render(src.substring(bodyStart, close))
                    out.append(math.text)
                    for (span in math.spans) {
                        spans.add(InlineSpan(start + span.start, start + span.end, span.style))
                    }
                    spans.add(InlineSpan(start, out.length, InlineStyle.MATH))
                    i = close + fence.length
                    continue
                }
            }

            // 3) 链接与图片
            if (c == '[' || (c == '!' && i + 1 < src.length && src[i + 1] == '[')) {
                val bracketAt = if (c == '!') i + 1 else i
                val parsed = parseLink(src, bracketAt)
                if (parsed != null) {
                    val (label, url, end) = parsed
                    // 注意：label 内部按普通样式展开（不加 LINK），
                    // 否则每个字符都会生成一个 url 为空的 LINK 区间，取 url 时会取错。
                    if (c == '!') add(out, spans, "🖼 ", styles)
                    val start = out.length
                    emit(label, out, spans, styles)
                    spans.add(InlineSpan(start, out.length, InlineStyle.LINK, url))
                    i = end
                    continue
                }
            }

            // 4) 尖括号自动链接 <https://...> 或 <a@b.com>
            if (c == '<') {
                val close = src.indexOf('>', i + 1)
                if (close > i) {
                    val inner = src.substring(i + 1, close).trim()
                    val url = when {
                        inner.startsWith("http://") || inner.startsWith("https://") -> inner
                        inner.contains('@') && !inner.contains(' ') -> "mailto:$inner"
                        else -> null
                    }
                    if (url != null) {
                        val start = out.length
                        out.append(inner)
                        spans.add(InlineSpan(start, out.length, InlineStyle.LINK, url))
                        i = close + 1
                        continue
                    }
                }
            }

            // 5) 裸链接
            if ((c == 'h' || c == 'H') && isBareUrlStart(src, i)) {
                var j = i
                while (j < src.length && !src[j].isWhitespace() && src[j] !in "<>\"'（）【】，。") j++
                val url = src.substring(i, j).trimEnd('.', ',', ';', ':', '!', '?', '、', '。', '，', '；')
                if (url.length > 10) {
                    val start = out.length
                    out.append(url)
                    spans.add(InlineSpan(start, out.length, InlineStyle.LINK, url))
                    i += url.length
                    continue
                }
            }

            // 6) 强调标记（长的优先，否则 ** 会被当成两个 *）
            val marker = when {
                src.startsWith("**", i) -> "**"
                src.startsWith("__", i) -> "__"
                src.startsWith("~~", i) -> "~~"
                src.startsWith("*", i) -> "*"
                src.startsWith("_", i) -> "_"
                else -> null
            }
            if (marker != null && canOpen(src, i, marker)) {
                val close = findClosing(src, i + marker.length, marker)
                if (close > i) {
                    val style = when (marker) {
                        "**", "__" -> InlineStyle.BOLD
                        "~~" -> InlineStyle.STRIKE
                        else -> InlineStyle.ITALIC
                    }
                    emit(src.substring(i + marker.length, close), out, spans, styles + style)
                    i = close + marker.length
                    continue
                }
            }

            add(out, spans, c.toString(), styles)
            i++
        }
    }

    private fun add(
        out: StringBuilder,
        spans: MutableList<InlineSpan>,
        text: String,
        styles: Set<InlineStyle>,
    ) {
        if (text.isEmpty()) return
        val start = out.length
        out.append(text)
        for (style in styles) spans.add(InlineSpan(start, out.length, style))
    }

    /** `[label](url "title")`，返回 (label, url, 结束下标) */
    private fun parseLink(src: String, bracketAt: Int): Triple<String, String, Int>? {
        if (bracketAt >= src.length || src[bracketAt] != '[') return null
        val closeBracket = src.indexOf(']', bracketAt + 1)
        if (closeBracket < 0 || closeBracket + 1 >= src.length) return null
        if (src[closeBracket + 1] != '(') return null
        val closeParen = src.indexOf(')', closeBracket + 2)
        if (closeParen < 0) return null
        val label = src.substring(bracketAt + 1, closeBracket)
        var url = src.substring(closeBracket + 2, closeParen).trim()
        val space = url.indexOf(' ')
        if (space > 0) url = url.substring(0, space)     // 丢掉 "title"
        return Triple(label, url, closeParen + 1)
    }

    /** 找公式的结束定界符：结束符前面不能是空格或反斜杠 */
    private fun findMathClose(src: String, from: Int, fence: String): Int {
        var idx = src.indexOf(fence, from)
        while (idx != -1) {
            val prevOk = idx > from && !src[idx - 1].isWhitespace() && src[idx - 1] != '\\'
            if (prevOk) return idx
            idx = src.indexOf(fence, idx + fence.length)
        }
        return -1
    }

    /** 找结束标记：要求结束标记前面不是空白，避免 "* 列表" 之类被误判 */
    private fun findClosing(src: String, from: Int, marker: String): Int {
        var idx = src.indexOf(marker, from)
        while (idx != -1) {
            if (idx > from && !src[idx - 1].isWhitespace()) return idx
            idx = src.indexOf(marker, idx + 1)
        }
        return -1
    }

    /** `_` 不能紧跟在字母数字后面，否则 my_var_name 会被拆坏 */
    private fun canOpen(src: String, at: Int, marker: String): Boolean {
        if (marker != "_") return true
        val prev = if (at == 0) ' ' else src[at - 1]
        return !prev.isLetterOrDigit()
    }

    private fun isBareUrlStart(src: String, i: Int): Boolean {
        val ok = src.regionMatches(i, "http://", 0, 7, ignoreCase = true) ||
            src.regionMatches(i, "https://", 0, 8, ignoreCase = true)
        if (!ok) return false
        val prev = if (i == 0) ' ' else src[i - 1]
        return !prev.isLetterOrDigit() && prev != '/'
    }

    private fun runLength(src: String, from: Int, ch: Char): Int {
        var n = 0
        while (from + n < src.length && src[from + n] == ch) n++
        return n
    }
}
