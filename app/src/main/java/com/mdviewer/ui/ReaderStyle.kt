package com.mdviewer.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * 阅读界面的排版参数。
 *
 * 用 CompositionLocal 往下传，这样各个 Block 组件不用一层层接参数，
 * 改设置时整棵树一起重组。
 */
data class ReaderStyle(
    /** 正文字号 sp */
    val fontSizeSp: Int = 16,
    /** 左右页边距 dp */
    val marginDp: Int = 16,
    /** 行距百分比，175 表示 1.75 倍 */
    val lineHeightPercent: Int = 175,
    /** 段落之间的额外间距 dp */
    val paragraphSpacingDp: Int = 6,
) {
    /** 正文行高（sp） */
    val lineHeightSp: Float get() = fontSizeSp * lineHeightPercent / 100f

    /** 段落上下的内边距 */
    val paragraphPaddingDp: Int get() = paragraphSpacingDp / 2 + 2

    /** 按正文字号的倍数算出别的字号 */
    fun scaled(ratio: Float): Float = fontSizeSp * ratio

    val h1: Float get() = scaled(1.70f)
    val h2: Float get() = scaled(1.35f)
    val h3: Float get() = scaled(1.18f)
    val h4: Float get() = scaled(1.05f)
    val code: Float get() = scaled(0.86f)
    val caption: Float get() = scaled(0.80f)
}

val LocalReaderStyle = compositionLocalOf { ReaderStyle() }
