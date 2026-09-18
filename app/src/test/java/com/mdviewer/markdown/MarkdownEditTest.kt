package com.mdviewer.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditTest {

    @Test
    fun wrap_selected_text() {
        val edit = MarkdownEdit.wrap("abc", 1, 2, "**")
        assertEquals("a**b**c", edit.text)
        // 包起来之后仍然选中原来的内容，方便继续点别的按钮
        assertEquals(3, edit.selectionStart)
        assertEquals(4, edit.selectionEnd)
    }

    @Test
    fun wrap_without_selection_puts_caret_in_the_middle() {
        val edit = MarkdownEdit.wrap("ac", 1, 1, "**")
        assertEquals("a****c", edit.text)
        assertEquals(3, edit.selectionStart)
        assertEquals(3, edit.selectionEnd)
    }

    @Test
    fun wrap_accepts_out_of_range_selection() {
        // 输入法偶尔会给越界的选区，不能崩
        val edit = MarkdownEdit.wrap("ab", 5, 9, "*")
        assertEquals("ab**", edit.text)
        assertEquals(3, edit.selectionStart)
    }

    @Test
    fun line_prefix_is_added() {
        val edit = MarkdownEdit.toggleLinePrefix("hello\nworld", 0, 0, "- ")
        // 只动光标所在的那一行
        assertEquals("- hello\nworld", edit.text)
    }

    @Test
    fun line_prefix_applies_to_all_selected_lines() {
        val edit = MarkdownEdit.toggleLinePrefix("a\nb", 0, 3, "- [ ] ")
        assertEquals("- [ ] a\n- [ ] b", edit.text)
    }

    @Test
    fun line_prefix_toggles_off() {
        val once = MarkdownEdit.toggleLinePrefix("a\nb", 0, 3, "- ")
        assertEquals("- a\n- b", once.text)
        val twice = MarkdownEdit.toggleLinePrefix(once.text, once.selectionStart, once.selectionEnd, "- ")
        assertEquals("a\nb", twice.text)
    }

    @Test
    fun heading_marker_toggles_on_and_off() {
        val on = MarkdownEdit.toggleLinePrefix("标题", 0, 2, "# ")
        assertEquals("# 标题", on.text)
        val off = MarkdownEdit.toggleLinePrefix(on.text, 0, 0, "# ")
        assertEquals("标题", off.text)
    }

    @Test
    fun toggle_only_touches_the_current_line() {
        val src = "# 一\n# 二\n三"
        // 光标放在「二」里面（下标 6）
        val edit = MarkdownEdit.toggleLinePrefix(src, 6, 6, "# ")
        // 它已经是标题了，所以去掉，其它行不动
        assertEquals("# 一\n二\n三", edit.text)
    }

    @Test
    fun insert_link_selects_the_placeholder() {
        val edit = MarkdownEdit.insertLink("text", 0, 4)
        assertEquals("[text](url)", edit.text)
        assertEquals("url", edit.text.substring(edit.selectionStart, edit.selectionEnd))
    }

    @Test
    fun insert_code_block_wraps_content() {
        val edit = MarkdownEdit.insertCodeBlock("abc", 0, 3)
        assertEquals("```\nabc\n```\n", edit.text)
        assertEquals("abc", edit.text.substring(edit.selectionStart, edit.selectionEnd))
    }

    @Test
    fun insert_code_block_adds_missing_newlines() {
        val edit = MarkdownEdit.insertCodeBlock("x\ny", 2, 3)
        assertEquals("x\n```\ny\n```\n", edit.text)
    }

    @Test
    fun empty_document_is_safe() {
        val edit = MarkdownEdit.toggleLinePrefix("", 0, 0, "- ")
        assertEquals("- ", edit.text)
        assertEquals(0, edit.selectionStart)
    }
}
