package com.mdviewer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {

    @Test
    fun fromName_restores_saved_theme() {
        assertEquals(AppTheme.ORANGE, AppTheme.fromName("ORANGE"))
        assertEquals(AppTheme.SEPIA, AppTheme.fromName("SEPIA"))
    }

    @Test
    fun fromName_falls_back_to_default() {
        assertEquals(AppTheme.BLUE, AppTheme.fromName(null))
        assertEquals(AppTheme.BLUE, AppTheme.fromName(""))
        assertEquals(AppTheme.BLUE, AppTheme.fromName("不存在的主题"))
    }

    @Test
    fun picker_covers_every_theme() {
        // 设置面板里能选到的主题，必须和枚举里定义的一致
        assertEquals(AppTheme.entries.toSet(), AppTheme.picker.toSet())
    }

    @Test
    fun labels_are_unique() {
        val labels = AppTheme.entries.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }
}
