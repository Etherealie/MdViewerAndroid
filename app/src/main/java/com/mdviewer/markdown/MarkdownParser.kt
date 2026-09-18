package com.mdviewer.markdown

/**
 * 块级解析器：Markdown 源文本 -> [MdDocument]
 *
 * 支持：ATX 标题（`#` 后有没有空格都认，方便中文文档）、Setext 标题（=== / ---）、
 * 围栏代码块（``` 与 ~~~）、缩进代码块、有序/无序/嵌套列表、任务列表、
 * 表格（含左右居中）、引用（可嵌套）、水平线、段落，以及文首的 YAML front matter。
 *
 * 思路和 [InlineParser] 一样：逐行扫描 + 递归下降，纯 Kotlin，能在 JVM 上单测。
 */
object MarkdownParser {

    private val RE_FENCE = Regex("^\\s{0,3}(`{3,}|~{3,})\\s*([^\\s`]*)\\s*$")
    private val RE_ATX = Regex("^\\s{0,3}(#{1,6})[ \\t]+(.*?)[ \\t]*#*[ \\t]*$")
    private val RE_ATX_CJK = Regex("^\\s{0,3}(#{1,6})([\\u3000-\\u303f\\u4e00-\\u9fff\\uff00-\\uffef].*?)[ \\t]*$")
    private val RE_HR = Regex("^\\s{0,3}(?:(?:\\*[ \\t]*){3,}|(?:-[ \\t]*){3,}|(?:_[ \\t]*){3,})$")
    private val RE_UL = Regex("^([ \\t]*)([-*+])[ \\t]+(.*)$")
    private val RE_OL = Regex("^([ \\t]*)(\\d{1,9})[.)][ \\t]+(.*)$")
    private val RE_QUOTE = Regex("^\\s{0,3}>[ \\t]?(.*)$")
    private val RE_SETEXT = Regex("^\\s{0,3}(=+|-+)[ \\t]*$")
    private val RE_TABLE_SEP = Regex("^\\s*\\|?[ \\t]*:?-+:?[ \\t]*(?:\\|[ \\t]*:?-+:?[ \\t]*)*\\|?\\s*$")
    private val RE_TASK = Regex("^\\[([ xX])\\][ \\t]+(.*)$")
    private val RE_FM_KEY = Regex("^([A-Za-z_][\\w \\-.]*):[ \\t]*(.*)$")
    private val RE_SLUG_STRIP = Regex("[^\\w\\u3000-\\u303f\\u4e00-\\u9fff \\-]")

    // ------------------------------------------------------------------ 对外入口

    fun parse(source: String): MdDocument {
        val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
        val (lines, frontMatter) = splitFrontMatter(normalized.split("\n"))
        val state = State()
        val blocks = ArrayList<MdBlock>()
        if (frontMatter.isNotEmpty()) blocks.add(MdBlock.FrontMatter(frontMatter))
        blocks.addAll(parseBlocks(lines, state, 0))
        return MdDocument(blocks, state.toc)
    }

    /** 只要大纲时用（比如文件列表里显示章节数） */
    fun tocOf(source: String): List<TocEntry> = parse(source).toc

    // ------------------------------------------------------------------ 状态

    private class State {
        val toc = ArrayList<TocEntry>()
        private val seen = HashMap<String, Int>()

        fun heading(level: Int, rawText: String): Int {
            val plain = InlineParser.parse(rawText).text
            val id = slugify(plain)
            toc.add(TocEntry(level, plain, id))
            return toc.size - 1
        }

        private fun slugify(text: String): String {
            var s = RE_SLUG_STRIP.replace(text, "").trim().lowercase()
            s = s.replace(Regex("[ \\t]+"), "-")
            if (s.isEmpty()) s = "section"
            val n = (seen[s] ?: 0) + 1
            seen[s] = n
            return if (n == 1) s else "$s-$n"
        }
    }

    private class RawItem(val marker: String, val absLine: Int) {
        val body = ArrayList<String>()
    }

    // ------------------------------------------------------------------ front matter

    private fun splitFrontMatter(lines: List<String>): Pair<List<String>, List<Pair<String, String>>> {
        if (lines.isEmpty() || lines[0].trim() != "---") return lines to emptyList()
        for (j in 1 until minOf(lines.size, 300)) {
            if (lines[j].trim() == "---" || lines[j].trim() == "...") {
                val entries = ArrayList<Pair<String, String>>()
                for (k in 1 until j) {
                    val raw = lines[k].trim()
                    if (raw.isEmpty() || raw.startsWith("#")) continue
                    val m = RE_FM_KEY.matchEntire(raw)
                    if (m != null) {
                        entries.add(m.groupValues[1].trim() to m.groupValues[2].trim().trim('"', '\''))
                    } else if (entries.isNotEmpty()) {
                        val last = entries.removeAt(entries.size - 1)
                        entries.add(last.first to (last.second + " " + raw))
                    }
                }
                return lines.drop(j + 1) to entries
            }
        }
        return lines to emptyList()
    }

    // ------------------------------------------------------------------ 块级主循环

    /**
     * @param baseLine lines[0] 对应源文件里的第几行（从 0 数）。
     *        引用、列表会把自己的起始行传下去，这样每一项都能记住自己的源码行号。
     */
    private fun parseBlocks(lines: List<String>, st: State, depth: Int, baseLine: Int = 0): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            if (line.isBlank()) { i++; continue }

            // 1) 围栏代码块 ``` / ~~~
            val fence = RE_FENCE.matchEntire(line)
            if (fence != null) {
                val marker = fence.groupValues[1]
                val lang = fence.groupValues[2].ifBlank { null }
                val buf = ArrayList<String>()
                i++
                while (i < lines.size) {
                    val s = lines[i].trim()
                    if (s.length >= marker.length && s.isNotEmpty() && s.all { it == marker[0] }) { i++; break }
                    buf.add(lines[i])
                    i++
                }
                out.add(MdBlock.Code(lang, trimTrailingBlank(buf)))
                continue
            }

            // 2) ATX 标题
            val atx = RE_ATX.matchEntire(line) ?: RE_ATX_CJK.matchEntire(line)
            if (atx != null) {
                val level = atx.groupValues[1].length
                out.add(MdBlock.Heading(level, InlineParser.parse(atx.groupValues[2].trim())))
                st.heading(level, atx.groupValues[2].trim())
                i++
                continue
            }

            // 3) 水平线（放在列表之前判断，否则 "- - -" 会被当成列表）
            if (RE_HR.matchEntire(line) != null) {
                out.add(MdBlock.Divider)
                i++
                continue
            }

            // 4) 引用（可嵌套）
            if (RE_QUOTE.matchEntire(line) != null) {
                val quoteStart = i
                val buf = ArrayList<String>()
                while (i < lines.size) {
                    val m = RE_QUOTE.matchEntire(lines[i])
                    if (m != null) {
                        buf.add(m.groupValues[1]); i++
                    } else if (lines[i].isBlank() && i + 1 < lines.size && RE_QUOTE.matchEntire(lines[i + 1]) != null) {
                        buf.add(""); i++
                    } else break
                }
                out.add(MdBlock.Quote(parseBlocks(buf, st, depth + 1, baseLine + quoteStart)))
                continue
            }

            // 5) 表格：本行有 | 且下一行是分隔行
            if (line.contains('|') && i + 1 < lines.size && lines[i + 1].contains('|') &&
                RE_TABLE_SEP.matchEntire(lines[i + 1]) != null
            ) {
                val header = splitRow(line).map { InlineParser.parse(it) }
                val aligns = parseAligns(lines[i + 1])
                i += 2
                val rows = ArrayList<List<InlineText>>()
                while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
                    rows.add(splitRow(lines[i]).map { InlineParser.parse(it) })
                    i++
                }
                out.add(MdBlock.Table(header, aligns, rows))
                continue
            }

            // 6) 列表
            if (RE_UL.matchEntire(line) != null || RE_OL.matchEntire(line) != null) {
                val (block, next) = parseList(lines, i, st, depth, baseLine)
                out.add(block)
                i = next
                continue
            }

            // 7) 缩进代码块
            if (line.startsWith("    ") || line.startsWith("\t")) {
                val buf = ArrayList<String>()
                while (i < lines.size && lines[i].isNotBlank()) {
                    buf.add(dedent(lines[i], 4))
                    i++
                }
                out.add(MdBlock.Code(null, trimTrailingBlank(buf)))
                continue
            }

            // 8) 段落（可能以 setext 下划线结尾 -> 变成标题）
            val buf = ArrayList<String>()
            while (i < lines.size && lines[i].isNotBlank()) {
                val current = lines[i]
                if (buf.isNotEmpty() && RE_SETEXT.matchEntire(current) != null && RE_UL.matchEntire(current) == null) {
                    buf.add(current)
                    i++
                    break
                }
                if (buf.isNotEmpty() && startsBlock(current)) break
                buf.add(current)
                i++
            }

            val last = buf.lastOrNull()
            if (buf.size >= 2 && last != null && RE_SETEXT.matchEntire(last) != null) {
                val level = if (last.trim().startsWith("=")) 1 else 2
                val text = buf.dropLast(1).joinToString(" ") { it.trim() }
                out.add(MdBlock.Heading(level, InlineParser.parse(text)))
                st.heading(level, text)
                continue
            }

            // 软换行按空格拼接；行尾两个空格或反斜杠 -> 硬换行（保留 \n）
            val sb = StringBuilder()
            for (raw in buf) {
                val trimmedEnd = raw.trimEnd()
                val hard = (raw.length - trimmedEnd.length >= 2) || trimmedEnd.endsWith("\\")
                val text = if (trimmedEnd.endsWith("\\")) trimmedEnd.dropLast(1).trim() else raw.trim()
                sb.append(text)
                sb.append(if (hard) "\n" else " ")
            }
            val paragraphText = sb.toString().trimEnd()
            val trimmed = paragraphText.trim()
            // 整段只有一条 $$...$$ 时按"展示公式"居中显示
            val isDisplayMath = trimmed.length > 4 &&
                trimmed.startsWith("$$") && trimmed.endsWith("$$") &&
                trimmed.count { it == '$' } == 4
            val inline = InlineParser.parse(paragraphText)
            out.add(MdBlock.Paragraph(if (isDisplayMath) inline.copy(displayMath = true) else inline))
        }

        return out
    }

    private fun startsBlock(line: String): Boolean {
        if (line.isBlank()) return true
        if (line.startsWith("    ") || line.startsWith("\t")) return true
        if (RE_FENCE.matchEntire(line) != null) return true
        if (RE_ATX.matchEntire(line) != null || RE_ATX_CJK.matchEntire(line) != null) return true
        if (RE_QUOTE.matchEntire(line) != null) return true
        if (RE_UL.matchEntire(line) != null || RE_OL.matchEntire(line) != null) return true
        return RE_HR.matchEntire(line) != null
    }

    // ------------------------------------------------------------------ 列表

    private fun parseList(
        lines: List<String>,
        start: Int,
        st: State,
        depth: Int,
        baseLine: Int,
    ): Pair<MdBlock.BulletList, Int> {
        val firstLine = lines[start]
        val firstOl = RE_OL.matchEntire(firstLine)
        val firstUl = RE_UL.matchEntire(firstLine)
        val first = firstOl ?: firstUl!!
        val base = indentWidth(first.groupValues[1])
        val ordered = firstOl != null
        val startNumber = if (ordered) first.groupValues[2].trimEnd('.', ')').toIntOrNull() ?: 1 else 1

        val items = ArrayList<RawItem>()
        var i = start
        while (i < lines.size) {
            val line = lines[i]

            if (line.isBlank()) {
                var j = i
                while (j < lines.size && lines[j].isBlank()) j++
                if (j < lines.size && items.isNotEmpty()) {
                    val m = RE_UL.matchEntire(lines[j]) ?: RE_OL.matchEntire(lines[j])
                    val keepGoing = if (m != null) {
                        indentWidth(m.groupValues[1]) >= base
                    } else {
                        // 空行之后的缩进续行：缩进够深，仍属于上一条
                        leadingSpaces(lines[j]) >= base + items.last().marker.length + 1
                    }
                    if (keepGoing) {
                        // 补充几个空行就补几个 body 行，保证 body[k] 永远对应源码第 absLine+k 行，
                        // 不然嵌套列表的行号会整体偏掉
                        repeat(j - i) { items.last().body.add("") }
                        i = j
                        continue
                    }
                }
                break
            }

            val m = RE_UL.matchEntire(line) ?: RE_OL.matchEntire(line)
            if (m != null) {
                val indent = indentWidth(m.groupValues[1])
                val isOrdered = RE_OL.matchEntire(line) != null
                if (indent < base) break
                if (indent == base && isOrdered != ordered) break
                if (indent > base) {
                    if (items.isEmpty()) break
                    items.last().body.add(line)
                } else {
                    val item = RawItem(m.groupValues[2], baseLine + i)
                    item.body.add(m.groupValues[3])
                    items.add(item)
                }
                i++
                continue
            }

            if (items.isNotEmpty()) {
                items.last().body.add(line)
                i++
                continue
            }
            break
        }

        val rendered = items.map { raw ->
            val width = base + raw.marker.length + 1
            val body = ArrayList<String>()
            body.add(raw.body.firstOrNull() ?: "")
            for (k in 1 until raw.body.size) body.add(dedent(raw.body[k], width))

            var checked: Boolean? = null
            val task = RE_TASK.matchEntire(body[0])
            if (task != null) {
                checked = task.groupValues[1].equals("x", ignoreCase = true)
                body[0] = task.groupValues[2]
            }
            ListItem(
                marker = raw.marker,
                checked = checked,
                blocks = parseBlocks(body, st, depth + 1, raw.absLine),
                sourceLine = raw.absLine,
            )
        }

        return MdBlock.BulletList(rendered, ordered, startNumber) to i
    }

    // ------------------------------------------------------------------ 表格

    private fun splitRow(row: String): List<String> {
        var s = row.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|") && !s.endsWith("\\|")) s = s.dropLast(1)
        return s.split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }
    }

    private fun parseAligns(sep: String): List<ColumnAlign> =
        splitRow(sep).map { cell ->
            val left = cell.startsWith(":")
            val right = cell.endsWith(":")
            when {
                left && right -> ColumnAlign.CENTER
                right -> ColumnAlign.RIGHT
                else -> ColumnAlign.LEFT
            }
        }

    // ------------------------------------------------------------------ 缩进工具

    /** 代码块只去掉**结尾**的空行，中间的空行必须保留 */
    private fun trimTrailingBlank(lines: List<String>): String =
        lines.dropLastWhile { it.isBlank() }.joinToString("\n")

    private fun indentWidth(prefix: String): Int {
        var n = 0
        for (ch in prefix) n += if (ch == '\t') 4 else 1
        return n
    }

    private fun leadingSpaces(line: String): Int {
        var n = 0
        while (n < line.length && (line[n] == ' ' || line[n] == '\t')) n++
        return n
    }

    private fun dedent(line: String, width: Int): String {
        var cut = 0
        while (cut < line.length && cut < width && (line[cut] == ' ' || line[cut] == '\t')) cut++
        return line.substring(cut)
    }
}
