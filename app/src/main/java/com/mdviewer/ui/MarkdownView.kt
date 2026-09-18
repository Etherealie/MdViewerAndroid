package com.mdviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdviewer.markdown.ColumnAlign
import com.mdviewer.markdown.InlineStyle
import com.mdviewer.markdown.InlineText
import com.mdviewer.markdown.ListItem
import com.mdviewer.markdown.MdBlock

/**
 * 把 [InlineText]（纯文本 + 样式区间）转成 Compose 的 AnnotatedString。
 * 这是「解析层」和「渲染层」之间唯一的桥梁。
 */
@Composable
fun InlineText.toAnnotated(): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val codeFg = if (MaterialTheme.colorScheme.background.isDarkColor()) Color(0xFF79C0FF) else Color(0xFF953800)
    val size = LocalReaderStyle.current.fontSizeSp.toFloat()
    val source = this

    return remember(source, linkColor, codeBg, codeFg, size) {
        buildAnnotatedString {
            append(source.text)
            for (span in source.spans) {
                if (span.start >= span.end || span.end > source.text.length) continue
                when (span.style) {
                    InlineStyle.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), span.start, span.end)
                    InlineStyle.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)
                    InlineStyle.STRIKE -> addStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough),
                        span.start,
                        span.end,
                    )
                    InlineStyle.CODE -> addStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg, color = codeFg),
                        span.start,
                        span.end,
                    )
                    // 公式：整体斜体，下标/上标缩小并做基线偏移
                    InlineStyle.MATH -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)
                    InlineStyle.SUB -> addStyle(
                        SpanStyle(fontSize = (size * 0.72f).sp, baselineShift = BaselineShift.Subscript),
                        span.start,
                        span.end,
                    )
                    InlineStyle.SUPER -> addStyle(
                        SpanStyle(fontSize = (size * 0.72f).sp, baselineShift = BaselineShift.Superscript),
                        span.start,
                        span.end,
                    )
                    InlineStyle.LINK -> {
                        val url = span.url ?: continue
                        addLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                                ),
                            ),
                            span.start,
                            span.end,
                        )
                    }
                }
            }
        }
    }
}

private fun Color.isDarkColor(): Boolean = (red * 0.299f + green * 0.587f + blue * 0.114f) < 0.5f

// ---------------------------------------------------------------------------
// 块级渲染
// ---------------------------------------------------------------------------

@Composable
fun HeadingBlock(block: MdBlock.Heading, modifier: Modifier = Modifier) {
    val reader = LocalReaderStyle.current
    val size = when (block.level) {
        1 -> reader.h1
        2 -> reader.h2
        3 -> reader.h3
        4 -> reader.h4
        else -> reader.fontSizeSp.toFloat()
    }
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = if (block.level <= 2) 18.dp else 12.dp, bottom = 6.dp),
    ) {
        Text(
            text = block.text.toAnnotated(),
            fontSize = size.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = (size * 1.35f).sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (block.level <= 2) {
            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
fun CodeBlock(block: MdBlock.Code, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val reader = LocalReaderStyle.current
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.language ?: "text",
                fontSize = reader.caption.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onCopy(block.code) }) {
                Text("复制", fontSize = reader.caption.sp)
            }
        }
        // 代码不折行、横向滚动，缩进才不会被压扁
        Text(
            text = block.code,
            fontFamily = FontFamily.Monospace,
            fontSize = reader.code.sp,
            lineHeight = (reader.code * 1.55f).sp,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }
}

@Composable
fun QuoteBlock(block: MdBlock.Quote, modifier: Modifier = Modifier) {
    Row(
        // IntrinsicSize.Min + fillMaxHeight：让左侧色条跟随内容高度
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(IntrinsicSize.Min)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Column(
            Modifier
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
                .fillMaxWidth(),
        ) {
            block.children.forEach { child -> BlockView(child) }
        }
    }
}

@Composable
fun ListBlock(block: MdBlock.BulletList, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        block.items.forEachIndexed { index, item ->
            ListRow(item = item, marker = if (block.ordered) "${block.start + index}." else "•")
        }
    }
}

@Composable
private fun ListRow(item: ListItem, marker: String) {
    val reader = LocalReaderStyle.current
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Box(
            Modifier
                .width(if (item.checked != null) 26.dp else 24.dp)
                .padding(top = 4.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            if (item.checked != null) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = null,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    text = marker,
                    fontSize = reader.fontSizeSp.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            item.blocks.forEach { child -> BlockView(child, isInsideList = true) }
        }
    }
}

@Composable
fun TableBlock(block: MdBlock.Table, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        TableRow(block.header, block.aligns, header = true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        block.rows.forEachIndexed { index, row ->
            TableRow(row, block.aligns, header = false)
            if (index != block.rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<InlineText>, aligns: List<ColumnAlign>, header: Boolean) {
    val reader = LocalReaderStyle.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (header) MaterialTheme.colorScheme.surfaceVariant else Color.Unspecified),
    ) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell.toAnnotated(),
                fontSize = reader.scaled(0.94f).sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                textAlign = when (aligns.getOrNull(index) ?: ColumnAlign.LEFT) {
                    ColumnAlign.LEFT -> TextAlign.Start
                    ColumnAlign.CENTER -> TextAlign.Center
                    ColumnAlign.RIGHT -> TextAlign.End
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
fun FrontMatterBlock(block: MdBlock.FrontMatter, modifier: Modifier = Modifier) {
    val reader = LocalReaderStyle.current
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = if (expanded) "文档信息（点击收起）" else "文档信息 · ${block.entries.size} 项（点击展开）",
            fontSize = reader.caption.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            block.entries.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = key,
                        fontSize = reader.caption.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(
                        text = value,
                        fontSize = reader.caption.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 单个块的渲染入口。
 * 阅读页把它放进 LazyColumn（一个块一项，方便按标题跳转），
 * 列表/引用内部直接递归调用。
 */
@Composable
fun BlockView(
    block: MdBlock,
    onCopy: (String) -> Unit = {},
    isInsideList: Boolean = false,
) {
    val reader = LocalReaderStyle.current
    when (block) {
        is MdBlock.Heading -> HeadingBlock(block)
        is MdBlock.Paragraph -> Text(
            text = block.text.toAnnotated(),
            fontSize = reader.fontSizeSp.sp,
            lineHeight = reader.lineHeightSp.sp,
            textAlign = if (block.text.displayMath) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = if (isInsideList) 2.dp else 5.dp,
                    bottom = if (isInsideList) 2.dp else 5.dp,
                ),
        )
        is MdBlock.Code -> CodeBlock(block, onCopy)
        is MdBlock.BulletList -> ListBlock(block)
        is MdBlock.Quote -> QuoteBlock(block)
        is MdBlock.Table -> TableBlock(block)
        MdBlock.Divider -> HorizontalDivider(
            Modifier.padding(vertical = 14.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        is MdBlock.FrontMatter -> FrontMatterBlock(block)
    }
}

/** 非懒加载版本，短文档 / 预览用 */
@Composable
fun MarkdownBody(blocks: List<MdBlock>, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val reader = LocalReaderStyle.current
    SelectionContainer {
        Column(
            modifier
                .fillMaxWidth()
                .padding(horizontal = reader.marginDp.dp, vertical = 8.dp),
        ) {
            blocks.forEach { BlockView(it, onCopy) }
            Spacer(Modifier.height(48.dp))
        }
    }
}
