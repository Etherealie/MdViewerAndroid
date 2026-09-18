package com.mdviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdviewer.data.SettingsStore
import kotlin.math.roundToInt

/** 字号预设：不想拖滑块就点一个 */
private val FONT_PRESETS = listOf("小" to 13, "标准" to 16, "大" to 20, "特大" to 24, "超大" to 30)

/** 字体预设，key 要和 ReaderStyle 里认的那几个对上 */
private val FONT_FAMILIES = listOf(
    "SYSTEM" to "系统默认",
    "SERIF" to "衷线",
    "MONO" to "等宽",
)

/**
 * 阅读设置面板。
 *
 * 因为 [SettingsStore] 里的值就是 Compose State，
 * 改任何一项后面正文都会实时跟着变，不用点"确定"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(settings: SettingsStore, onDismiss: () -> Unit) {
    val dark = isSystemInDarkTheme()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "阅读设置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { settings.reset() }) { Text("恢复默认", fontSize = 13.sp) }
            }

            // ---------------- 字号 ----------------
            SectionTitle("字号")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FONT_PRESETS.forEach { (label, size) ->
                    PresetChip(label = label, selected = settings.fontSizeSp == size) {
                        settings.setFontSize(size)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            SliderRow(
                label = "正文字号",
                value = settings.fontSizeSp,
                text = "${settings.fontSizeSp} sp",
                range = SettingsStore.FONT_MIN.toFloat()..SettingsStore.FONT_MAX.toFloat(),
                steps = SettingsStore.FONT_MAX - SettingsStore.FONT_MIN - 1,
            ) { settings.setFontSize(it) }

            // ---------------- 字体 ----------------
            SectionTitle("字体")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FONT_FAMILIES.forEach { (key, label) ->
                    PresetChip(label = label, selected = settings.fontFamilyKey == key) {
                        settings.updateFontFamilyKey(key)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            // ---------------- 排版 ----------------
            SectionTitle("排版")
            SliderRow(
                label = "段落间距",
                value = settings.paragraphSpacingDp,
                text = "${settings.paragraphSpacingDp} dp",
                range = SettingsStore.PARAGRAPH_MIN.toFloat()..SettingsStore.PARAGRAPH_MAX.toFloat(),
                steps = 19,
            ) { settings.setParagraphSpacing(it) }
            SliderRow(
                label = "左右页边距",
                value = settings.marginDp,
                text = "${settings.marginDp} dp",
                range = SettingsStore.MARGIN_MIN.toFloat()..SettingsStore.MARGIN_MAX.toFloat(),
                steps = 23,
            ) { settings.setMargin(it) }
            SliderRow(
                label = "行距",
                value = settings.lineHeightPercent,
                text = "${settings.lineHeightPercent}%",
                range = SettingsStore.LINE_MIN.toFloat()..SettingsStore.LINE_MAX.toFloat(),
                steps = 19,
            ) { settings.setLineHeight(it) }

            // ---------------- 配色 ----------------
            SectionTitle("配色")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppTheme.picker.forEach { theme ->
                    ThemeDot(
                        theme = theme,
                        dark = dark,
                        selected = AppTheme.fromName(settings.themeName) == theme,
                    ) { settings.updateThemeName(theme.name) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "当前：${AppTheme.fromName(settings.themeName).label}" +
                    if (settings.themeName == AppTheme.DYNAMIC.name) "（需 Android 12 以上，低版本会退回经典蓝）" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---------------- 显示 ----------------
            SectionTitle("显示")
            SwitchRow(
                title = "显示滚动条",
                subtitle = "内容超出一屏时，右侧显示一条细滚动条",
                checked = settings.showScrollbar,
            ) { settings.setScrollbarVisible(it) }
            SwitchRow(
                title = "阅读时屏幕常亮",
                subtitle = "看文档时不让屏幕自动熄灭",
                checked = settings.keepScreenOn,
            ) { settings.updateKeepScreenOn(it) }
            SwitchRow(
                title = "显示阅读进度",
                subtitle = "在标题下方显示当前读到的百分比",
                checked = settings.showProgress,
            ) { settings.updateShowProgress(it) }

            // ---------------- 预览 ----------------
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Text("预览效果", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Column(Modifier.padding(horizontal = settings.marginDp.dp)) {
                val previewFont = LocalReaderStyle.current.fontFamily
                Text(
                    text = "攻角 α 与升力系数 C\u2097 的关系见式 (3.10)，当 α∈[6°, 18°] 时近似线性。",
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineHeightPercent / 100f).sp,
                    fontFamily = previewFont,
                )
                Spacer(Modifier.height(settings.paragraphSpacingDp.dp))
                Text(
                    text = "这是第二段，用来观察段落间距和行距。",
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineHeightPercent / 100f).sp,
                    fontFamily = previewFont,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun PresetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ThemeDot(theme: AppTheme, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(theme.accentColor(dark))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = theme.label,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Int,
    text: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text(
                text,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.End,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range,
            steps = steps.coerceAtLeast(0),
        )
    }
}
