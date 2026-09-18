package com.mdviewer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownSourceHighlighterTest {

    private fun kindAt(src: String, index: Int): SourceKind? =
        MarkdownSourceHighlighter.spans(src)
            .firstOrNull { index >= it.start && index < it.end }
            ?.kind

    private fun textOf(src: String, kind: SourceKind): List<String> =
        MarkdownSourceHighlighter.spans(src)
            .filter { it.kind == kind }
            .map { src.substring(it.start, it.end) }

    @Test
    fun heading_line_is_highlighted() {
        val src = "# 标题\n正文"
        assertEquals(SourceKind.HEADING, kindAt(src, 0))
        assertEquals(SourceKind.HEADING, kindAt(src, 2))
        assertNull(kindAt(src, 6)) // 「正」不能算标题
    }

    @Test
    fun list_and_quote_markers() {
        assertEquals(SourceKind.MARKER, kindAt("- 一项", 0))
        assertEquals(SourceKind.MARKER, kindAt("- 一项", 1))
        assertNull(kindAt("- 一项", 2))

        assertEquals(SourceKind.MARKER, kindAt("> 引用", 0))
        assertEquals(SourceKind.MARKER, kindAt("> 引用", 1))
    }

    @Test
    fun task_marker_covers_the_checkbox() {
        assertEquals(listOf("- [ ] "), textOf("- [ ] 待办", SourceKind.MARKER))
        assertEquals(listOf("- [x] "), textOf("- [x] 已完成", SourceKind.MARKER))
    }

    @Test
    fun ordered_list_marker() {
        assertEquals(listOf("12. "), textOf("12. 第十二条", SourceKind.MARKER))
    }

    @Test
    fun fenced_code_body_is_code_block() {
        val src = "```c\nint a = 1;\n```\n"
        assertEquals(listOf("```c", "```"), textOf(src, SourceKind.MARKER))
        assertEquals(listOf("int a = 1;"), textOf(src, SourceKind.CODE_BLOCK))
    }

    @Test
    fun inline_code_and_link() {
        assertEquals(listOf("`code`"), textOf("看 `code` 这里", SourceKind.CODE))
        assertEquals(listOf("(http://a.b)"), textOf("[文字](http://a.b)", SourceKind.LINK))
    }

    @Test
    fun emphasis_markers_are_marked() {
        assertEquals(listOf("**", "**"), textOf("**粗**", SourceKind.MARKER))
        assertEquals(listOf("*", "*"), textOf("普通 *斜* 字", SourceKind.MARKER))
        assertEquals(listOf("~~", "~~"), textOf("~~删~~", SourceKind.MARKER))
    }

    @Test
    fun spans_stay_in_bounds_and_in_order() {
        val src = """
            # 标题

            - [ ] 待办 **加粗**
            - 普通项 `code`

            > 引用 [链接](http://x)

            ```py
            print(1)
            ```
        """.trimIndent()
        val spans = MarkdownSourceHighlighter.spans(src)
        assertTrue(spans.isNotEmpty())
        var last = 0
        for (span in spans) {
            assertTrue("下标必须合法：$span", span.start in 0..span.end && span.end <= src.length)
            assertTrue("必须按序：$span", span.start >= last)
            last = span.start
        }
    }

    @Test
    fun empty_source_is_safe() {
        assertTrue(MarkdownSourceHighlighter.spans("").isEmpty())
        assertTrue(MarkdownSourceHighlighter.spans("\n\n").isEmpty())
    }

    @Test
    fun no_newline_at_the_end_is_fine() {
        assertEquals(listOf("# 没有换行结尾"), textOf("# 没有换行结尾", SourceKind.HEADING))
    }
}
