package com.mdviewer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单元测试：在 Android Studio 里右键这个文件 -> Run 就能跑，
 * 也可以在项目根目录执行 `gradlew test`。
 */
class MarkdownParserTest {

    private fun parse(src: String) = MarkdownParser.parse(src)

    @Test
    fun headings_and_toc() {
        val doc = parse("# 一级\n\n## 二级\n")
        assertEquals(2, doc.toc.size)
        assertEquals(1, doc.toc[0].level)
        assertEquals("一级", doc.toc[0].text)
        assertTrue(doc.blocks[0] is MdBlock.Heading)
    }

    @Test
    fun cjk_heading_without_space() {
        val doc = parse("###中文标题\n")
        val heading = doc.blocks.first() as MdBlock.Heading
        assertEquals(3, heading.level)
        assertEquals("中文标题", heading.text.text)
    }

    @Test
    fun duplicate_heading_ids() {
        val doc = parse("# A\n\n# A\n")
        assertEquals(listOf("a", "a-2"), doc.toc.map { it.id })
    }

    @Test
    fun fenced_code_keeps_content_verbatim() {
        val doc = parse("```python\nprint(1) < 2\n```\n")
        val code = doc.blocks.first() as MdBlock.Code
        assertEquals("python", code.language)
        assertEquals("print(1) < 2", code.code)
    }

    @Test
    fun nested_list() {
        val doc = parse("- a\n  - a1\n  - a2\n- b\n")
        val list = doc.blocks.first() as MdBlock.BulletList
        assertEquals(2, list.items.size)
        // 第一项里应包含一个子列表
        assertTrue(list.items[0].blocks.any { it is MdBlock.BulletList })
    }

    @Test
    fun ordered_list_start_number() {
        val doc = parse("3. c\n4. d\n")
        val list = doc.blocks.first() as MdBlock.BulletList
        assertTrue(list.ordered)
        assertEquals(3, list.start)
    }

    @Test
    fun task_list() {
        val doc = parse("- [x] 完成\n- [ ] 未完成\n")
        val list = doc.blocks.first() as MdBlock.BulletList
        assertEquals(true, list.items[0].checked)
        assertEquals(false, list.items[1].checked)
    }

    @Test
    fun table_with_alignment() {
        val doc = parse("| A | B |\n|:--|--:|\n| 1 | 2 |\n")
        val table = doc.blocks.first() as MdBlock.Table
        assertEquals(2, table.header.size)
        assertEquals(ColumnAlign.LEFT, table.aligns[0])
        assertEquals(ColumnAlign.RIGHT, table.aligns[1])
        assertEquals(1, table.rows.size)
    }

    @Test
    fun horizontal_rule_not_list() {
        val doc = parse("- - -\n")
        assertTrue(doc.blocks.first() is MdBlock.Divider)
    }

    @Test
    fun front_matter() {
        val doc = parse("---\ntitle: 测试\n---\n\n正文\n")
        assertEquals(listOf("title" to "测试"), doc.frontMatter)
        assertTrue(doc.blocks.first() is MdBlock.FrontMatter)
        assertTrue(doc.blocks[1] is MdBlock.Paragraph)
    }

    @Test
    fun inline_styles_produce_spans() {
        val text = InlineParser.parse("**粗体** 和 *斜体* 和 `代码`")
        assertEquals("粗体 和 斜体 和 代码", text.text)
        assertTrue(text.spans.any { it.style == InlineStyle.BOLD })
        assertTrue(text.spans.any { it.style == InlineStyle.ITALIC })
        assertTrue(text.spans.any { it.style == InlineStyle.CODE })
    }

    @Test
    fun inline_link_carries_url() {
        val text = InlineParser.parse("见 [官网](https://a.com)")
        assertEquals("见 官网", text.text)
        assertEquals("https://a.com", text.spans.first { it.style == InlineStyle.LINK }.url)
    }

    @Test
    fun snake_case_is_not_italic() {
        val text = InlineParser.parse("my_var_name")
        assertEquals("my_var_name", text.text)
        assertTrue(text.spans.isEmpty())
    }

    @Test
    fun escaped_markers_are_literal() {
        val text = InlineParser.parse("\\*不是斜体\\*")
        assertEquals("*不是斜体*", text.text)
        assertTrue(text.spans.isEmpty())
    }

    @Test
    fun hard_break_becomes_newline() {
        val doc = parse("上  \n下\n")
        val paragraph = doc.blocks.first() as MdBlock.Paragraph
        assertTrue(paragraph.text.text.contains("\n"))
    }

    @Test
    fun soft_break_becomes_space() {
        val doc = parse("第一行\n第二行\n")
        val paragraph = doc.blocks.first() as MdBlock.Paragraph
        assertEquals("第一行 第二行", paragraph.text.text)
    }

    @Test
    fun quote_can_nest() {
        val doc = parse("> 外层\n> > 内层\n")
        val quote = doc.blocks.first() as MdBlock.Quote
        assertTrue(quote.children.any { it is MdBlock.Quote })
    }

    @Test
    fun weird_input_does_not_crash() {
        listOf("", "\n\n", "#", "```", "|", "- ", "> ", "", "\\", "[", "[](", "	tab").forEach {
            parse(it)
        }
    }

    @Test
    fun crlf_is_normalized() {
        val doc = parse("# t\r\n\r\n正文\r\n")
        assertTrue(doc.blocks.first() is MdBlock.Heading)
    }

    @Test
    fun blank_document_has_no_blocks() {
        val doc = parse("")
        assertTrue(doc.blocks.isEmpty())
        assertTrue(doc.toc.isEmpty())
    }
}
