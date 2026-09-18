package com.mdviewer.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class TextStatsTest {

    @Test
    fun chinese_counts_per_character() {
        assertEquals(4, TextStats.countWords("攻角为零"))
    }

    @Test
    fun english_counts_per_word() {
        assertEquals(3, TextStats.countWords("hello big world"))
    }

    @Test
    fun mixed_text_matches_typora_style() {
        // 攻角(2) + alpha(1) + 为(1) + 6(1) + 度(1) = 6
        assertEquals(6, TextStats.countWords("攻角 alpha 为 6 度"))
    }

    @Test
    fun apostrophe_and_hyphen_keep_words_together() {
        assertEquals(2, TextStats.countWords("it's well-known"))
    }

    @Test
    fun punctuation_does_not_count() {
        assertEquals(0, TextStats.countWords("，。！？—— …… ,.!?"))
    }

    @Test
    fun japanese_kana_counts_too() {
        // カ タ カ ナ 四个字符
        assertEquals(4, TextStats.countWords("カタカナ"))
    }

    @Test
    fun empty_text() {
        assertEquals(0, TextStats.countWords(""))
        assertEquals(0, TextStats.countChars(""))
        assertEquals(0, TextStats.countLines(""))
    }

    @Test
    fun chars_and_lines() {
        assertEquals(5, TextStats.countChars("a\nb\nc"))
        assertEquals(3, TextStats.countLines("a\nb\nc"))
        // 结尾有换行时，编辑器里还能看到最后那个空行，所以是 3 行
        assertEquals(3, TextStats.countLines("a\nb\n"))
        assertEquals(1, TextStats.countLines("只有一行"))
    }

    @Test
    fun markdown_symbols_add_words() {
        // # 和 - 会被当成词边界，这符合"字面统计"，不去猜 Markdown 语义
        assertEquals(4, TextStats.countWords("# 标题\n\n- 一项"))
    }
}
