package com.mdviewer.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * 阅读界面的字号/边距/行距。
 *
 * 用 CompositionLocal 往下传，这样各个 Block 组件不用一层层接参数，
 * 改设置时整棵树一起重组。
 */
data class ReaderStyle(
    val fontSizeSp: Int = 16,
    val marginDp: Int = 16,
    val lineHeightPercent: Int = 175,
) {
    /** 正文行高（sp） */
    val lineHeightSp: Float get() = fontSizeSp * lineHeightPercent / 100f

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
