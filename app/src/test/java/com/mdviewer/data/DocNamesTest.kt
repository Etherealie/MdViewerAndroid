package com.mdviewer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocNamesTest {

    @Test
    fun appends_markdown_extension() {
        assertEquals("我的笔记.md", DocNames.normalize("我的笔记"))
    }

    @Test
    fun keeps_existing_extension() {
        assertEquals("笔记.txt", DocNames.normalize("笔记.txt"))
        assertEquals("readme.md", DocNames.normalize("readme.md"))
    }

    @Test
    fun strips_illegal_characters() {
        assertEquals("abc.md", DocNames.normalize("a/b:c*?"))
        assertEquals("ab.md", DocNames.normalize("a\\b"))
    }

    @Test
    fun trims_whitespace() {
        assertEquals("笔记.md", DocNames.normalize("  笔记  "))
    }

    @Test
    fun rejects_empty() {
        assertNull(DocNames.normalize(""))
        assertNull(DocNames.normalize("   "))
        assertNull(DocNames.normalize("///"))
        assertNull(DocNames.normalize("..."))
    }

    @Test
    fun avoids_hidden_file() {
        assertEquals("_.隐藏.md", DocNames.normalize(".隐藏"))
    }

    @Test
    fun limits_length() {
        val long = "a".repeat(200)
        assertEquals(60, DocNames.normalize(long)!!.length)
    }
}
