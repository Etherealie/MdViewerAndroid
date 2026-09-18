package com.mdviewer.markdown

/** 源码着色的类别 */
enum class SourceKind {
    /** 整行标题 */
    HEADING,

    /** 语法符号本身：# - * > ` [ ] 等 */
    MARKER,

    /** 行内代码 */
    CODE,

    /** 围栏代码块里的内容 */
    CODE_BLOCK,

    /** 链接地址部分 */
    LINK,
}

/** 一段原文的下标区间（左闭右开） */
data class SourceSpan(val start: Int, val end: Int, val kind: SourceKind)

/**
 * 编辑模式下的 Markdown 源码着色。
 *
 * 只负责“哪一段是什么”，不改动任何字符——返回的下标和原文一一对应，
 * 这样把它塞进 VisualTransformation 时光标位置不会错位。
 */
object MarkdownSourceHighlighter {

    fun spans(source: String): List<SourceSpan> {
        val spans = ArrayList<SourceSpan>()
        var lineStart = 0
        var inFence = false

        while (lineStart <= source.length) {
            var lineEnd = source.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = source.length

            val line = source.substring(lineStart, lineEnd)
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            when {
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    spans.add(SourceSpan(lineStart + indent, lineEnd, SourceKind.MARKER))
                    inFence = !inFence
                }

                inFence -> {
                    if (lineEnd > lineStart) spans.add(SourceSpan(lineStart, lineEnd, SourceKind.CODE_BLOCK))
                }

                trimmed.startsWith("#") -> {
                    spans.add(SourceSpan(lineStart + indent, lineEnd, SourceKind.HEADING))
                }

                else -> {
                    val marker = leadingMarker(trimmed)
                    if (marker > 0) {
                        val from = lineStart + indent
                        spans.add(SourceSpan(from, from + marker, SourceKind.MARKER))
                    }
                    inlineSpans(source, lineStart + indent + marker, lineEnd, spans)
                }
            }

            if (lineEnd >= source.length) break
            lineStart = lineEnd + 1
        }
        return spans
    }

    /** 行首标记的长度：`> `、`- `、`* `、`1. `、`- [ ] ` */
    private fun leadingMarker(s: String): Int {
        var i = 0
        while (i < s.length && s[i] == '>') {
            i++
            if (i < s.length && s[i] == ' ') i++
        }
        if (i > 0) return i

        if (s.length >= 2 && (s[0] == '-' || s[0] == '*' || s[0] == '+') && s[1] == ' ') {
            if (s.length >= 5 && s[2] == '[' && s[4] == ']' &&
                (s[3] == ' ' || s[3] == 'x' || s[3] == 'X')
            ) {
                var j = 5
                while (j < s.length && s[j] == ' ') j++
                return j
            }
            return 2
        }

        var digits = 0
        while (digits < s.length && s[digits].isDigit()) digits++
        if (digits in 1..3 && digits + 1 < s.length && s[digits] == '.' && s[digits + 1] == ' ') {
            return digits + 2
        }
        return 0
    }

    /** 行内的 `code`、**粗体**、*斜体*、~~删除线~~、[文字](链接) */
    private fun inlineSpans(source: String, from: Int, to: Int, spans: MutableList<SourceSpan>) {
        var i = from
        while (i < to) {
            when (val c = source[i]) {
                '`' -> {
                    val close = source.indexOf('`', i + 1)
                    if (close in (i + 1) until to) {
                        spans.add(SourceSpan(i, close + 1, SourceKind.CODE))
                        i = close + 1
                    } else i++
                }

                '*', '_', '~' -> {
                    val len = if (i + 1 < to && source[i + 1] == c) 2 else 1
                    val pair = if (len == 2) "$c$c" else "$c"
                    spans.add(SourceSpan(i, i + len, SourceKind.MARKER))
                    val close = source.indexOf(pair, i + len)
                    if (close in (i + len) until to) {
                        spans.add(SourceSpan(close, close + len, SourceKind.MARKER))
                        i = close + len
                    } else {
                        i += len
                    }
                }

                '[' -> {
                    val close = source.indexOf(']', i + 1)
                    if (close in (i + 1) until to) {
                        spans.add(SourceSpan(i, i + 1, SourceKind.MARKER))
                        spans.add(SourceSpan(close, close + 1, SourceKind.MARKER))
                        if (close + 1 < to && source[close + 1] == '(') {
                            val paren = source.indexOf(')', close + 2)
                            if (paren in (close + 2)..to) {
                                spans.add(SourceSpan(close + 1, paren + 1, SourceKind.LINK))
                            }
                        }
                        i = close + 1
                    } else i++
                }

                else -> i++
            }
        }
    }
}
