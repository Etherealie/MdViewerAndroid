package com.mdviewer.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    private fun pieces(code: String, lang: String?): List<Pair<String, TokenKind>> =
        SyntaxHighlighter.tokenize(code, lang).map { code.substring(it.start, it.end) to it.kind }

    private fun first(code: String, lang: String?, kind: TokenKind): String? =
        pieces(code, lang).firstOrNull { it.second == kind }?.first

    @Test
    fun unknown_language_produces_nothing() {
        assertTrue(SyntaxHighlighter.tokenize("int a = 1;", null).isEmpty())
        assertTrue(SyntaxHighlighter.tokenize("int a = 1;", "纯文本").isEmpty())
    }

    @Test
    fun c_line_comment_runs_to_end_of_line() {
        val code = "int a = 1; // 这里都是注释\nint b = 2;"
        assertEquals("// 这里都是注释", first(code, "c", TokenKind.COMMENT))
    }

    @Test
    fun c_block_comment_spans_lines() {
        val code = "/* 第一行\n第二行 */ int a;"
        val comment = first(code, "cpp", TokenKind.COMMENT)
        assertTrue(comment!!.startsWith("/* 第一行"))
        assertTrue(comment.endsWith("*/"))
    }

    @Test
    fun python_hash_comment() {
        assertEquals("# 说明", first("x = 1  # 说明", "python", TokenKind.COMMENT))
    }

    @Test
    fun hash_inside_string_is_not_a_comment() {
        val pieces = pieces("print(\"# 不是注释\")", "python")
        assertFalse(pieces.any { it.second == TokenKind.COMMENT })
        assertEquals("\"# 不是注释\"", pieces.first { it.second == TokenKind.STRING }.first)
    }

    @Test
    fun string_with_escape_stays_one_token() {
        val code = "char *s = \"a\\\"b\";"
        assertEquals("\"a\\\"b\"", first(code, "c", TokenKind.STRING))
    }

    @Test
    fun unterminated_string_stops_at_line_end() {
        val code = "s = \"没有结尾\nnext"
        val str = first(code, "python", TokenKind.STRING)!!
        assertFalse(str.contains("next"))
    }

    @Test
    fun keywords_and_types() {
        assertEquals("return", first("return 0;", "c", TokenKind.KEYWORD))
        assertEquals("String", first("String name = \"x\";", "java", TokenKind.TYPE))
        assertEquals("def", first("def f():", "python", TokenKind.KEYWORD))
    }

    @Test
    fun capitalized_identifier_looks_like_a_type() {
        assertEquals("HttpClient", first("val c = HttpClient()", "kotlin", TokenKind.TYPE))
        // 单个大写字母不算，避免把 A / T 之类当成类型
        assertTrue(pieces("val t: T", "kotlin").none { it.first == "T" })
    }

    @Test
    fun numbers_are_kept_whole() {
        assertEquals("3.14", first("let x = 3.14;", "js", TokenKind.NUMBER))
        assertEquals("0xFF", first("int m = 0xFF;", "c", TokenKind.NUMBER))
    }

    @Test
    fun shell_keywords() {
        val code = "for f in *.txt; do echo \$f; done"
        val words = pieces(code, "bash")
        assertTrue(words.any { it.first == "for" && it.second == TokenKind.KEYWORD })
        assertTrue(words.any { it.first == "done" && it.second == TokenKind.KEYWORD })
        assertFalse(words.any { it.second == TokenKind.COMMENT })
    }

    @Test
    fun tokens_are_sorted_and_do_not_overlap() {
        val code = """
            // 注释
            struct Node {
                char *name;   /* 名字 */
                int   size;   // 大小
                double ratio;
            };
            const char *msg = "hello \"world\"";
        """.trimIndent()
        val tokens = SyntaxHighlighter.tokenize(code, "c")
        assertTrue(tokens.isNotEmpty())
        var last = -1
        for (t in tokens) {
            assertTrue("区间必须合法：$t", t.start < t.end && t.end <= code.length)
            assertTrue("必须按序且不重叠：$t", t.start >= last)
            last = t.end
        }
    }

    @Test
    fun empty_code_is_safe() {
        assertTrue(SyntaxHighlighter.tokenize("", "c").isEmpty())
    }
}
