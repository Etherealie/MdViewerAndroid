package com.mdviewer.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/**
 * 可选的配色主题。
 *
 * 每套只定义"主色"，浅色/深色两套界面的其余颜色由 [buildScheme] 推导，
 * 这样加一套新主题只要加一行。
 */
enum class AppTheme(
    val label: String,
    /** 浅色模式下的主色（0xAARRGGBB） */
    val light: Long,
    /** 深色模式下的主色 */
    val dark: Long,
) {
    BLUE("经典蓝", 0xFF0969DA, 0xFF4493F8),
    GREEN("森林绿", 0xFF1A7F37, 0xFF3FB950),
    TEAL("青碧", 0xFF0D8A8A, 0xFF2CBDBD),
    PURPLE("静谧紫", 0xFF8250DF, 0xFFA371F7),
    ROSE("绯樱", 0xFFBF3989, 0xFFDB61A2),
    ORANGE("日落橙", 0xFFBC4C00, 0xFFDB6D28),
    MONO("石墨灰", 0xFF57606A, 0xFF8B949E),
    SEPIA("米黄护眼", 0xFF8A6A2F, 0xFFD3A85C),
    DYNAMIC("跟随壁纸", 0xFF0969DA, 0xFF4493F8),   // Android 12+ 用系统取色
    ;

    val isSepia: Boolean get() = this == SEPIA

    companion object {
        /** 从保存的字符串还原，认不出来就回到默认 */
        fun fromName(name: String?): AppTheme =
            entries.firstOrNull { it.name == name } ?: BLUE

        /** 给设置面板用的顺序列表 */
        val picker: List<AppTheme> =
            listOf(BLUE, GREEN, TEAL, PURPLE, ROSE, ORANGE, MONO, SEPIA, DYNAMIC)
    }
}

/** 深色模式下用于预览色点的颜色 */
fun AppTheme.accentColor(dark: Boolean): Color = Color(if (dark) this.dark else this.light)

@Composable
fun rememberColorScheme(theme: AppTheme, dark: Boolean): ColorScheme {
    val context = LocalContext.current
    return remember(theme, dark) {
        if (theme == AppTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 跟随系统壁纸取色（Android 12 及以上）
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            buildScheme(theme, dark)
        }
    }
}

private fun buildScheme(theme: AppTheme, dark: Boolean): ColorScheme {
    val accent = Color(if (dark) theme.dark else theme.light)
    val sepia = theme.isSepia

    val bg = when {
        sepia && dark -> Color(0xFF1A1712)
        sepia -> Color(0xFFFAF4E6)
        dark -> Color(0xFF0D1117)
        else -> Color(0xFFFFFFFF)
    }
    val onBg = when {
        sepia && dark -> Color(0xFFEDE4D3)
        sepia -> Color(0xFF3A3226)
        dark -> Color(0xFFE6EDF3)
        else -> Color(0xFF1F2328)
    }
    val surfaceVariant = when {
        sepia && dark -> Color(0xFF262119)
        sepia -> Color(0xFFF2E9D5)
        dark -> Color(0xFF161B22)
        else -> Color(0xFFF6F8FA)
    }
    val onSurfaceVariant = when {
        sepia && dark -> Color(0xFFB5A88C)
        sepia -> Color(0xFF6B5E42)
        dark -> Color(0xFF9198A1)
        else -> Color(0xFF59636E)
    }
    val outline = when {
        sepia && dark -> Color(0xFF3A3226)
        sepia -> Color(0xFFDFD3B8)
        dark -> Color(0xFF30363D)
        else -> Color(0xFFD8DEE4)
    }

    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF04121F),
            // 容器色用主色和背景混合，避免半透明叠色发灰
            primaryContainer = lerp(accent, bg, 0.78f),
            onPrimaryContainer = Color(0xFFE6EDF3),
            background = bg,
            onBackground = onBg,
            surface = bg,
            onSurface = onBg,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outlineVariant = outline,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = lerp(accent, bg, 0.86f),
            onPrimaryContainer = accent,
            background = bg,
            onBackground = onBg,
            surface = bg,
            onSurface = onBg,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outlineVariant = outline,
        )
    }
}
