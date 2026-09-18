package com.mdviewer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdviewer.data.SettingsStore
import kotlin.math.roundToInt

/**
 * 阅读设置面板：字号 / 页边距 / 行距 / 滚动条开关。
 *
 * 因为 [SettingsStore] 里的值就是 Compose State，
 * 拖动滑块时后面的正文会实时跟着变，不用点"确定"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(settings: SettingsStore, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("阅读设置", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { settings.reset() }) { Text("恢复默认", fontSize = 13.sp) }
            }

            SliderRow(
                label = "正文字号",
                value = settings.fontSizeSp,
                text = "${settings.fontSizeSp} sp",
                range = SettingsStore.FONT_MIN.toFloat()..SettingsStore.FONT_MAX.toFloat(),
                steps = SettingsStore.FONT_MAX - SettingsStore.FONT_MIN - 1,
            ) { settings.setFontSize(it) }

            SliderRow(
                label = "左右边距",
                value = settings.marginDp,
                text = "${settings.marginDp} dp",
                range = SettingsStore.MARGIN_MIN.toFloat()..SettingsStore.MARGIN_MAX.toFloat(),
                steps = 11,
            ) { settings.setMargin(it) }

            SliderRow(
                label = "行距",
                value = settings.lineHeightPercent,
                text = "${settings.lineHeightPercent}%",
                range = SettingsStore.LINE_MIN.toFloat()..SettingsStore.LINE_MAX.toFloat(),
                steps = 9,
            ) { settings.setLineHeight(it) }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("显示滚动条", fontSize = 15.sp)
                    Text(
                        "内容超出一屏时，右侧显示一条细滚动条",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = settings.showScrollbar, onCheckedChange = { settings.setScrollbarVisible(it) })
            }

            Spacer(Modifier.height(18.dp))

            // 实时预览
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = settings.marginDp.dp, vertical = 10.dp),
            ) {
                Text(
                    "预览效果",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "攻角 α 与升力系数 C\u2097 的关系见式 (3.10)，" +
                        "当 α∈[6°, 18°] 时近似线性。",
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineHeightPercent / 100f).sp,
                )
            }
        }
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
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
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
