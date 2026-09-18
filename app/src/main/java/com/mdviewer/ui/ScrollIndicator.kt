package com.mdviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 一条悬浮在右侧的细滚动条。
 *
 * Compose 的 LazyColumn 默认不画滚动条，这里自己实现：
 *   - 位置：用第一个可见项的「下标 + 项内偏移比例」估算整体进度
 *   - 长度：可见项数 / 总项数，并按内容比例换算
 *   - 交互：可以拖动，也可以点轨道直接跳（按比例定位到对应块）
 *
 * 因为每个 Markdown 块高度不完全一样，位置是近似值，
 * 但用来"看进度、快速拖到大概位置"完全够用。
 */
@Composable
fun ScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled) return

    val layout = state.layoutInfo
    val total = layout.totalItemsCount
    val visible = layout.visibleItemsInfo
    if (total <= 1 || visible.isEmpty()) return

    val first = visible.first()
    val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
    val firstFraction = (first.offset - layout.viewportStartOffset).toFloat() / first.size.coerceAtLeast(1)
    val progress = ((first.index + firstFraction) / total).coerceIn(0f, 1f)
    val thumbFraction = (visible.size.toFloat() / total).coerceIn(0.08f, 1f)

    // 内容没超出屏幕就不用显示
    if (thumbFraction >= 0.995f) return

    val scope = rememberCoroutineScope()
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)

    fun jumpTo(fraction: Float) {
        val target = (fraction * total).toInt().coerceIn(0, total - 1)
        scope.launch { state.scrollToItem(target) }
    }

    Box(
        modifier
            .width(18.dp)
            .fillMaxHeight()
            .pointerInput(total) {
                detectVerticalDragGestures { change, _ ->
                    jumpTo(change.position.y / size.height.coerceAtLeast(1))
                }
            }
            .pointerInput(total) {
                detectTapGestures { offset ->
                    jumpTo(offset.y / size.height.coerceAtLeast(1))
                }
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(Modifier.fillMaxHeight().width(5.dp)) {
            Spacer(Modifier.weight((progress * (1f - thumbFraction)).coerceAtLeast(0.0001f)))
            Box(
                Modifier
                    .weight(thumbFraction)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
            Spacer(Modifier.weight(((1f - progress) * (1f - thumbFraction)).coerceAtLeast(0.0001f)))
        }
    }
}
