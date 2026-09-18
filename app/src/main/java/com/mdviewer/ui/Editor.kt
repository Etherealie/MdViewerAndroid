package com.mdviewer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdviewer.markdown.MarkdownEdit
import com.mdviewer.markdown.MarkdownSourceHighlighter
import com.mdviewer.markdown.SourceKind
import com.mdviewer.markdown.TextEdit

// ---------------------------------------------------------------------------
// 编辑工具栏：手机上敲 ** # ` 这些符号很麻烦，点一下就行
// ---------------------------------------------------------------------------

@Composable
fun MarkdownEditToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun run(edit: TextEdit) {
        onValueChange(
            TextFieldValue(edit.text, TextRange(edit.selectionStart, edit.selectionEnd)),
        )
    }

    val s = value.selection.min
    val e = value.selection.max

    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 2.dp),
        ) {
            ToolChip("B", bold = true) { run(MarkdownEdit.wrap(value.text, s, e, "**")) }
            ToolChip("I", italic = true) { run(MarkdownEdit.wrap(value.text, s, e, "*")) }
            ToolChip("S", strike = true) { run(MarkdownEdit.wrap(value.text, s, e, "~~")) }
            ToolChip("H1") { run(MarkdownEdit.toggleLinePrefix(value.text, s, e, "# ")) }
            ToolChip("H2") { run(MarkdownEdit.toggleLinePrefix(value.text, s, e, "## ")) }
            ToolChip("H3") { run(MarkdownEdit.toggleLinePrefix(value.text, s, e, "### ")) }
            ToolChip("行内码") { run(MarkdownEdit.wrap(value.text, s, e, "`")) }
            ToolChip("代码块") { run(MarkdownEdit.insertCodeBlock(value.text, s, e)) }
            ToolChip("链接") { run(MarkdownEdit.insertLink(value.text, s, e)) }
            ToolChip("列表") { run(MarkdownEdit.toggleLinePrefix(value.text, s, e, "- ")) }
            ToolChip("待办") { run(MarkdownEdit.toggleLinePrefix(value.text, s, e, "- [ ] ")) }
            ToolChip("引用") { run(MarkdownEdit.toggleLinePrefix(value.text, s, e, "> ")) }
        }
    }
}

@Composable
private fun ToolChip(
    label: String,
    bold: Boolean = false,
    italic: Boolean = false,
    strike: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(40.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (strike) TextDecoration.LineThrough else null,
        )
    }
}

// ---------------------------------------------------------------------------
// 编辑时的源码着色
// ---------------------------------------------------------------------------

@Composable
fun rememberMarkdownSourceHighlight(): VisualTransformation {
    val dark = isSystemInDarkTheme()
    return remember(dark) { MarkdownSourceHighlight(dark) }
}

/**
 * 给源码上色，但一个字符都不改——返回的 AnnotatedString 与原文等长，
 * 所以 OffsetMapping.Identity 是安全的，光标和选区不会跑偏。
 */
class MarkdownSourceHighlight(private val dark: Boolean) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val styled = buildAnnotatedString {
            var last = 0
            for (span in MarkdownSourceHighlighter.spans(source)) {
                val start = span.start.coerceAtLeast(last)
                val end = span.end.coerceAtMost(source.length)
                if (end <= start) continue
                if (start > last) append(source.substring(last, start))
                withStyle(spanStyle(span.kind, dark)) { append(source.substring(start, end)) }
                last = end
            }
            if (last < source.length) append(source.substring(last))
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

private fun spanStyle(kind: SourceKind, dark: Boolean): SpanStyle = when (kind) {
    SourceKind.HEADING -> SpanStyle(
        color = if (dark) Color(0xFF79C0FF) else Color(0xFF0550AE),
        fontWeight = FontWeight.Bold,
    )

    SourceKind.MARKER -> SpanStyle(
        color = if (dark) Color(0xFF8B949E) else Color(0xFF6E7781),
    )

    SourceKind.CODE -> SpanStyle(
        color = if (dark) Color(0xFFFFA657) else Color(0xFF953800),
    )

    SourceKind.CODE_BLOCK -> SpanStyle(
        color = if (dark) Color(0xFFA5D6FF) else Color(0xFF0A3069),
    )

    SourceKind.LINK -> SpanStyle(
        color = if (dark) Color(0xFF79C0FF) else Color(0xFF0969DA),
        textDecoration = TextDecoration.Underline,
    )
}
