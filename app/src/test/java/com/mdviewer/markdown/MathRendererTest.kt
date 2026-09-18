package com.mdviewer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 公式转换器的单元测试：`gradlew test` 或 Studio 里右键运行 */
class MathRendererTest {

    @Test
    fun greek_letters() {
        assertEquals("α", MathRenderer.plain("\\alpha"))
        assertEquals("Δ", MathRenderer.plain("\\Delta"))
        assertEquals("ω", MathRenderer.plain("\\omega"))
    }

    @Test
    fun operators_and_relations() {
        assertEquals("×", MathRenderer.plain("\\times"))
        assertEquals("≤", MathRenderer.plain("\\leq"))
        assertEquals("≈", MathRenderer.plain("\\approx"))
        assertEquals("→∞", MathRenderer.plain("\\to\\infty"))
    }

    @Test
    fun subscript_is_marked() {
        val result = MathRenderer.render("C_L")
        assertEquals("CL", result.text)
        val sub = result.spans.first { it.style == InlineStyle.SUB }
        assertEquals("L", result.text.substring(sub.start, sub.end))
    }

    @Test
    fun braced_subscript_keeps_group() {
        val result = MathRenderer.render("C_{L\\alpha}")
        assertEquals("CLα", result.text)
        val covered = result.spans
            .filter { it.style == InlineStyle.SUB }
            .flatMap { (it.start until it.end).toList() }
            .toSet()
        assertEquals(setOf(1, 2), covered)   // L 和 α 都是下标
    }

    @Test
    fun superscript_and_nested() {
        val result = MathRenderer.render("x^{2}")
        assertEquals("x2", result.text)
        assertTrue(result.spans.any { it.style == InlineStyle.SUPER })
    }

    @Test
    fun fraction_gets_parentheses_when_needed() {
        // C_L 里的下划线会被转成下标，所以渲染出来是 CL
        assertEquals("(CL)/2", MathRenderer.plain("\\frac{C_L}{2}"))
        assertEquals("a/b", MathRenderer.plain("\\frac{a}{b}"))
    }

    @Test
    fun sqrt() {
        assertEquals("√(x+1)", MathRenderer.plain("\\sqrt{x+1}"))
        assertEquals("√2", MathRenderer.plain("\\sqrt{2}"))
        assertEquals("√[3]x", MathRenderer.plain("\\sqrt[3]{x}"))
    }

    @Test
    fun real_world_formula_from_user_doc() {
        // 论文里常见的一条：CL = CL0 + CLα·α + ΔCL
        val text = MathRenderer.plain("C_L = C_{L0} + C_{L\\alpha}\\,\\alpha + \\Delta C_L")
        assertEquals("CL = CL0 + CLα α + Δ CL", text)
    }

    @Test
    fun second_real_formula() {
        val text = MathRenderer.plain("\\alpha = \\arctan(w/u)")
        // \arctan 是未知命令，退化成名字，至少不会丢内容
        assertEquals("α = arctan(w/u)", text)
    }

    @Test
    fun text_command_is_literal() {
        assertEquals("刹车量", MathRenderer.plain("\\text{刹车量}"))
    }

    @Test
    fun left_right_are_dropped() {
        assertEquals("(x)", MathRenderer.plain("\\left(x\\right)"))
    }

    @Test
    fun combining_marks() {
        assertEquals("x\u0304", MathRenderer.plain("\\bar{x}"))
        assertEquals("x\u0302", MathRenderer.plain("\\hat{x}"))
    }

    @Test
    fun spacing_commands_become_space() {
        assertEquals("a b", MathRenderer.plain("a\\,b"))
        // \\quad 本身转成一个空格，源码里那个空格也还在，所以是两个
        assertEquals("a  b", MathRenderer.plain("a\\quad b"))
    }

    @Test
    fun unknown_command_keeps_name() {
        assertEquals("foobar", MathRenderer.plain("\\foobar"))
    }

    @Test
    fun empty_input() {
        assertEquals("", MathRenderer.plain(""))
    }

    @Test
    fun no_crash_on_weird_input() {
        listOf("\\", "\\\\", "{", "}", "^{", "_{", "\\frac{", "\\sqrt[", "$", "{}", "^^^", "___")
            .forEach { MathRenderer.render(it) }
    }

    @Test
    fun inline_parser_turns_dollar_math_into_math_span() {
        val text = InlineParser.parse("攻角 \$\\alpha\$ 变化")
        assertEquals("攻角 α 变化", text.text)
        assertTrue(text.spans.any { it.style == InlineStyle.MATH })
    }

    @Test
    fun inline_math_keeps_subscript_span() {
        val text = InlineParser.parse("\$C_L\$")
        assertEquals("CL", text.text)
        assertTrue(text.spans.any { it.style == InlineStyle.SUB })
    }

    @Test
    fun dollar_amount_is_not_math() {
        val text = InlineParser.parse("价格 \$5 and \$10 元")
        assertTrue(text.spans.none { it.style == InlineStyle.MATH })
        assertEquals("价格 \$5 and \$10 元", text.text)
    }

    @Test
    fun display_math_paragraph_is_flagged() {
        val doc = MarkdownParser.parse("\$\$C_L = C_{L0} + \\Delta C_L\$\$\n")
        val paragraph = doc.blocks.first() as MdBlock.Paragraph
        assertTrue(paragraph.text.displayMath)
        assertTrue(paragraph.text.spans.any { it.style == InlineStyle.MATH })
    }

    @Test
    fun inline_math_paragraph_is_not_display() {
        val doc = MarkdownParser.parse("前面 \$x^2\$ 后面\n")
        val paragraph = doc.blocks.first() as MdBlock.Paragraph
        assertEquals(false, paragraph.text.displayMath)
    }
}
