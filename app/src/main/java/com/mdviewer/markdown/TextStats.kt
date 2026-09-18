package com.mdviewer.markdown

/**
 * 文档字数统计。
 *
 * 中文按"字"算，英文按"单词"算（和 Word / Typora 的口径一致）：
 *   "攻角 alpha 为 6 度" -> 攻角(2) + alpha(1) + 为(1) + 6(1) + 度(1) = 6
 */
object TextStats {

    /** 中日韩统一表意文字 + 常用中文标点之外的 CJK 区段 */
    private fun isCjk(c: Char): Boolean =
        c.code in 0x4E00..0x9FFF ||      // 基本区
            c.code in 0x3400..0x4DBF ||  // 扩展 A
            c.code in 0xF900..0xFAFF ||  // 兼容表意文字
            c.code in 0x3040..0x30FF     // 日文假名（也算一个字）

    /** 字数：CJK 逐字计数，其它语言按连续字母/数字段计数 */
    fun countWords(text: String): Int {
        var count = 0
        var inWord = false
        for (c in text) {
            when {
                isCjk(c) -> {
                    count++
                    inWord = false
                }
                c.isLetterOrDigit() -> {
                    if (!inWord) {
                        count++
                        inWord = true
                    }
                }
                c == '\'' || c == '-' -> {
                    // 英文里的 it's / well-known 算一个词，不打断
                }
                else -> inWord = false
            }
        }
        return count
    }

    /** 字符数（含空白，用于"文件大小"式的描述） */
    fun countChars(text: String): Int = text.length

    /** 行数 */
    fun countLines(text: String): Int = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
}
