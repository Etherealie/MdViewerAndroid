package com.mdviewer.markdown

/**
 * 行内样式的种类。
 * 这一层刻意不依赖 Compose / Android，方便写 JVM 单元测试。
 */
enum class InlineStyle {
    BOLD, ITALIC, STRIKE, CODE, LINK,

    /** 数学公式整体 */
    MATH,

    /** 公式里的下标 / 上标 */
    SUB, SUPER,
}

/** 某段文本上的一个样式区间：[start, end) */
data class InlineSpan(
    val start: Int,
    val end: Int,
    val style: InlineStyle,
    val url: String? = null,
)

/** 解析后的行内文本 = 去掉标记的纯文本 + 样式区间 */
data class InlineText(
    val text: String,
    val spans: List<InlineSpan> = emptyList(),
    /** 整段就是一个 $$...$$ 展示公式时置 true，渲染层会居中显示 */
    val displayMath: Boolean = false,
) {
    val isBlank: Boolean get() = text.isBlank()

    /** 去掉所有样式，只要纯文本（大纲、搜索用） */
    fun plain(): String = text

    companion object {
        val EMPTY = InlineText("")
    }
}

enum class ColumnAlign { LEFT, CENTER, RIGHT }

/** 列表的一项：可以带嵌套的子块（下一级列表、代码块等） */
data class ListItem(
    val marker: String,
    val checked: Boolean? = null,      // 任务列表：[ ] -> false，[x] -> true，普通项 -> null
    val blocks: List<MdBlock>,
)

/** 块级元素 */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: InlineText) : MdBlock
    data class Paragraph(val text: InlineText) : MdBlock
    data class Code(val language: String?, val code: String) : MdBlock
    data class BulletList(
        val items: List<ListItem>,
        val ordered: Boolean,
        val start: Int,
    ) : MdBlock

    data class Quote(val children: List<MdBlock>) : MdBlock
    data class Table(
        val header: List<InlineText>,
        val aligns: List<ColumnAlign>,
        val rows: List<List<InlineText>>,
    ) : MdBlock

    data object Divider : MdBlock
    data class FrontMatter(val entries: List<Pair<String, String>>) : MdBlock
}

/** 大纲条目 */
data class TocEntry(val level: Int, val text: String, val id: String)

/** 一次解析的完整结果 */
data class MdDocument(
    val blocks: List<MdBlock>,
    val toc: List<TocEntry>,
) {
    val frontMatter: List<Pair<String, String>>
        get() = (blocks.firstOrNull() as? MdBlock.FrontMatter)?.entries ?: emptyList()
}
