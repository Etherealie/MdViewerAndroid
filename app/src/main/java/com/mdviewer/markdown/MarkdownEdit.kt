package com.mdviewer.markdown

/** 一次编辑的结果：新文本 + 新的光标/选区 */
data class TextEdit(val text: String, val selectionStart: Int, val selectionEnd: Int)

/**
 * 编辑工具栏要做的几件事。全部是纯字符串运算，方便单测。
 * 所有下标都会先 coerce 到合法范围，输入法给出的选区有时会越界。
 */
object MarkdownEdit {

    /** 把选中内容包起来（没选中就插一对符号，光标停中间） */
    fun wrap(text: String, start: Int, end: Int, prefix: String, suffix: String = prefix): TextEdit {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val inner = text.substring(s, e)
        val newText = text.substring(0, s) + prefix + inner + suffix + text.substring(e)
        return if (inner.isEmpty()) {
            val caret = s + prefix.length
            TextEdit(newText, caret, caret)
        } else {
            TextEdit(newText, s + prefix.length, s + prefix.length + inner.length)
        }
    }

    /**
     * 给选中的每一行加/去行首标记（#、- 、>、- [ ] 之类）。
     * 全都已经有这个标记时再点一下就去掉，等于一个开关。
     */
    fun toggleLinePrefix(text: String, start: Int, end: Int, marker: String): TextEdit {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val lineStart = if (s == 0) 0 else (text.lastIndexOf('\n', s - 1) + 1).coerceAtLeast(0)
        var lineEnd = text.indexOf('\n', e)
        if (lineEnd < 0) lineEnd = text.length

        val block = text.substring(lineStart, lineEnd)
        val lines = block.split("\n")
        val allHave = lines.all { it.startsWith(marker) }
        val newBlock = lines.joinToString("\n") { line ->
            when {
                allHave -> line.removePrefix(marker)
                line.startsWith(marker) -> line
                else -> marker + line
            }
        }
        if (newBlock == block) {
            return TextEdit(text, s, e)
        }
        val newText = text.substring(0, lineStart) + newBlock + text.substring(lineEnd)
        return TextEdit(newText, lineStart, lineStart + newBlock.length)
    }

    /** 插入链接，并把 url 占位符选中，粘贴上去就能替换 */
    fun insertLink(text: String, start: Int, end: Int, placeholder: String = "url"): TextEdit {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val label = text.substring(s, e)
        val newText = text.substring(0, s) + "[" + label + "](" + placeholder + ")" + text.substring(e)
        val urlStart = s + 1 + label.length + 2
        return TextEdit(newText, urlStart, urlStart + placeholder.length)
    }

    /** 插入围栏代码块；选中内容会放进去，语言标签自己敲 */
    fun insertCodeBlock(text: String, start: Int, end: Int): TextEdit {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val inner = text.substring(s, e)
        val head = if (s == 0 || text[s - 1] == '\n') "" else "\n"
        val tail = if (e >= text.length || text[e] == '\n') "" else "\n"
        val newText = text.substring(0, s) + head + "```\n" + inner + "\n```\n" + text.substring(e)
        val innerStart = s + head.length + 4
        return TextEdit(newText, innerStart, innerStart + inner.length)
    }
}
