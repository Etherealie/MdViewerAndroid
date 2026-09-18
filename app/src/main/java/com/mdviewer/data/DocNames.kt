package com.mdviewer.data

/**
 * 新建文件时对用户输入的文件名做规整。
 *
 * 单独放一个对象、不碰任何 Android API，这样可以直接跑 JVM 单元测试。
 */
object DocNames {

    /** 文件系统不允许出现的字符 */
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")

    private const val MAX_LENGTH = 60

    /**
     * 把用户输入变成合法的文件名；输入为空或全是非法字符时返回 null。
     *
     * - `我的笔记`      -> `我的笔记.md`（自动补扩展名）
     * - `笔记.txt`      -> `笔记.txt`（已有扩展名就不动）
     * - `a/b:c*?`       -> `abc.md`（去掉非法字符）
     */
    fun normalize(input: String): String? {
        val cleaned = ILLEGAL.replace(input.trim(), "").trim().trimEnd('.')
        if (cleaned.isEmpty()) return null
        // 开头就是点的情况（.gitignore）不算有扩展名
        val hasExtension = cleaned.indexOf('.') > 0
        var name = if (cleaned.startsWith(".")) "_$cleaned" else cleaned
        if (!hasExtension) name = "$name.md"
        if (name.length > MAX_LENGTH) {
            // 截断时保留扩展名，总长度不超过上限
            val dot = name.lastIndexOf('.')
            val ext = if (dot >= 0) name.substring(dot) else ""
            name = name.substring(0, (MAX_LENGTH - ext.length).coerceAtLeast(1)) + ext
        }
        return name
    }
}
