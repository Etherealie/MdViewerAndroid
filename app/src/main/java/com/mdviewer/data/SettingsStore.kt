package com.mdviewer.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 阅读偏好设置。
 *
 * 用 Compose 的 State 承载，所以在设置面板里拖动滑块时，
 * 后面的阅读界面会立刻跟着变（不用手动刷新）。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 正文字号，单位 sp */
    var fontSizeSp by mutableIntStateOf(prefs.getInt(KEY_FONT, DEFAULT_FONT))
        private set

    /** 正文左右边距，单位 dp */
    var marginDp by mutableIntStateOf(prefs.getInt(KEY_MARGIN, DEFAULT_MARGIN))
        private set

    /** 行距，百分比（175 表示 1.75 倍） */
    var lineHeightPercent by mutableIntStateOf(prefs.getInt(KEY_LINE, DEFAULT_LINE))
        private set

    /** 段落之间的额外间距，单位 dp */
    var paragraphSpacingDp by mutableIntStateOf(prefs.getInt(KEY_PARAGRAPH, DEFAULT_PARAGRAPH))
        private set

    /** 是否显示滚动条 */
    var showScrollbar by mutableStateOf(prefs.getBoolean(KEY_SCROLLBAR, true))
        private set

    /** 阅读时不让屏幕自动熄灭 */
    var keepScreenOn by mutableStateOf(prefs.getBoolean(KEY_KEEP_ON, false))
        private set

    /** 在标题栏显示阅读进度百分比 */
    var showProgress by mutableStateOf(prefs.getBoolean(KEY_PROGRESS, true))
        private set

    /** 配色主题名（存字符串是为了兼容以后重命名枚举） */
    var themeName by mutableStateOf(prefs.getString(KEY_THEME, "BLUE") ?: "BLUE")
        private set

    fun setFontSize(value: Int) {
        fontSizeSp = value.coerceIn(FONT_MIN, FONT_MAX)
        prefs.edit().putInt(KEY_FONT, fontSizeSp).apply()
    }

    fun setMargin(value: Int) {
        marginDp = value.coerceIn(MARGIN_MIN, MARGIN_MAX)
        prefs.edit().putInt(KEY_MARGIN, marginDp).apply()
    }

    fun setLineHeight(value: Int) {
        lineHeightPercent = value.coerceIn(LINE_MIN, LINE_MAX)
        prefs.edit().putInt(KEY_LINE, lineHeightPercent).apply()
    }

    fun setParagraphSpacing(value: Int) {
        paragraphSpacingDp = value.coerceIn(PARAGRAPH_MIN, PARAGRAPH_MAX)
        prefs.edit().putInt(KEY_PARAGRAPH, paragraphSpacingDp).apply()
    }

    fun setScrollbarVisible(value: Boolean) {
        showScrollbar = value
        prefs.edit().putBoolean(KEY_SCROLLBAR, showScrollbar).apply()
    }

    fun updateKeepScreenOn(value: Boolean) {
        keepScreenOn = value
        prefs.edit().putBoolean(KEY_KEEP_ON, keepScreenOn).apply()
    }

    fun updateShowProgress(value: Boolean) {
        showProgress = value
        prefs.edit().putBoolean(KEY_PROGRESS, showProgress).apply()
    }

    fun updateThemeName(value: String) {
        themeName = value
        prefs.edit().putString(KEY_THEME, themeName).apply()
    }

    fun reset() {
        setFontSize(DEFAULT_FONT)
        setMargin(DEFAULT_MARGIN)
        setLineHeight(DEFAULT_LINE)
        setParagraphSpacing(DEFAULT_PARAGRAPH)
        setScrollbarVisible(true)
        updateKeepScreenOn(false)
        updateShowProgress(true)
        updateThemeName("BLUE")
    }

    companion object {
        private const val PREFS = "mdviewer_settings"
        private const val KEY_FONT = "font_size"
        private const val KEY_MARGIN = "margin"
        private const val KEY_LINE = "line_height"
        private const val KEY_PARAGRAPH = "paragraph_spacing"
        private const val KEY_SCROLLBAR = "show_scrollbar"
        private const val KEY_KEEP_ON = "keep_screen_on"
        private const val KEY_PROGRESS = "show_progress"
        private const val KEY_THEME = "theme"

        const val FONT_MIN = 10
        const val FONT_MAX = 40
        const val DEFAULT_FONT = 16

        const val MARGIN_MIN = 0
        const val MARGIN_MAX = 96
        const val DEFAULT_MARGIN = 16

        const val LINE_MIN = 100
        const val LINE_MAX = 300
        const val DEFAULT_LINE = 175

        const val PARAGRAPH_MIN = 0
        const val PARAGRAPH_MAX = 40
        const val DEFAULT_PARAGRAPH = 6
    }
}
