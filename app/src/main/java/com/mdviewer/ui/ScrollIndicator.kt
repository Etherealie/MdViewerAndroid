package com.mdviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 右侧细滚动条。
 *
 * ## 为什么不用"第几个块 / 总块数"
 * 上一版是 `(firstVisibleItemIndex + 块内比例) / totalItems`，在 Markdown 文档里抖得厉害：
 *   1. 各块高度差太多（标题 40px、正文 100px、代码块 600px），
 *      滚过一个高块，位置却只前进 1/total，看起来就是"一格一格跳"；
 *   2. 滑块长度按"可见块数 / 总块数"算，而可见块数在滚动时每帧 ±1
 *      （新块进来、旧块出去），长度跟着抖 —— 这是"颤抖"最直接的来源；
 *   3. 拖动只能 `scrollToItem(下标)`，按块对齐，也是跳的。
 *
 * ## 现在的做法：按像素算
 * 把每个块的**实测高度**记下来（[rememberItemHeights]），于是
 *   - 位置 = 前面各块实测高度之和 + 当前块内偏移（没量过的按平均值估）
 *   - 长度 = 视口高度 / 估算的总内容高度
 *   - 拖动/点击 = 按比例算出目标像素，再换算成 `scrollToItem(下标, 块内像素偏移)`，**像素级**定位
 *
 * 浏览过的块越多估算越准；短文档会全部测量，等于精确值。
 */
@Composable
fun ScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (!enabled) return

    val heights = rememberItemHeights(state)

    val layout = state.layoutInfo
    val total = layout.totalItemsCount
    val visible = layout.visibleItemsInfo
    if (total <= 1 || visible.isEmpty()) return

    val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
    val measuredSum = heights.values.sum()
    val measuredCount = heights.size
    // 还没测量过的块，按已测量部分的平均高度估
    val avg = if (measuredCount > 0) measuredSum.toFloat() / measuredCount else viewport.toFloat()
    val estimatedTotal = measuredSum + (total - measuredCount) * avg

    // 内容还没超出一屏，不用显示
    if (estimatedTotal <= viewport) return

    val first = visible.first()
    var before = 0f
    for (i in 0 until first.index) before += heights[i]?.toFloat() ?: avg
    val scrolled = before + (first.offset - layout.viewportStartOffset)

    val progress = (scrolled / estimatedTotal).coerceIn(0f, 1f)
    val thumbFraction = (viewport / estimatedTotal).coerceIn(0.05f, 1f)

    val scope = rememberCoroutineScope()
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    /** 把 0~1 的比例换算成"第几个块 + 块内多少像素"，实现像素级跳转 */
    fun jumpToFraction(fraction: Float) {
        val targetPx = fraction.coerceIn(0f, 1f) * estimatedTotal
        var acc = 0f
        var index = 0
        while (index < total - 1) {
            val h = heights[index]?.toFloat() ?: avg
            if (acc + h > targetPx) break
            acc += h
            index++
        }
        val offsetInItem = (targetPx - acc).toInt().coerceAtLeast(0)
        scope.launch { state.scrollToItem(index, offsetInItem) }
    }

    // 手势检测器只创建一次（pointerInput(Unit)），用 rememberUpdatedState 保证每次点击/拖动
    // 都用最新的跳转逻辑；否则滚动中 estimatedTotal 变化会重建检测器、打断正在进行的拖动。
    val jump by rememberUpdatedState({ fraction: Float -> jumpToFraction(fraction) })

    Box(
        modifier
            .width(20.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> jump(offset.y / size.height.coerceAtLeast(1)) },
                ) { change, _ ->
                    jump(change.position.y / size.height.coerceAtLeast(1))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> jump(offset.y / size.height.coerceAtLeast(1)) }
            },
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(Modifier.fillMaxHeight().width(5.dp)) {
            // 用 weight 分三段，比例即位置
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

/**
 * 记录每个块布局后的真实高度。
 *
 * 写入放在 [LaunchedEffect] 里，不污染组合过程；值没变时 SnapshotStateMap
 * 不会触发重组，所以不会白刷。
 */
@Composable
private fun rememberItemHeights(state: LazyListState): Map<Int, Int> {
    val heights = remember(state) { mutableStateMapOf<Int, Int>() }
    LaunchedEffect(state) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index to it.size } }
            .collect { pairs ->
                for ((index, size) in pairs) {
                    if (size > 0) heights[index] = size
                }
            }
    }
    return heights
}
