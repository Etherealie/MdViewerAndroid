package com.mdviewer.markdown

/** 词法单元的类型。颜色由 UI 层决定，这里只管分类。 */
enum class TokenKind { KEYWORD, STRING, COMMENT, NUMBER, TYPE }

/** 代码里的一个着色区间 [start, end) */
data class Token(val start: Int, val end: Int, val kind: TokenKind)

/**
 * 极简语法高亮：按语言把代码切成 关键字/字符串/注释/数字 四类。
 *
 * 目标不是编辑器级别的高亮，而是让代码块"一眼能分清哪是注释、哪是字符串"。
 * 纯 Kotlin、不依赖 Compose，可以直接跑单元测试。
 */
object SyntaxHighlighter {

    /** 语言家族，决定注释符号和关键字表 */
    private enum class Family { C_LIKE, PYTHON, SHELL, PLAIN }

    private val C_KEYWORDS = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue",
        "return", "goto", "sizeof", "typedef", "struct", "union", "enum", "static", "extern",
        "const", "volatile", "inline", "register", "auto", "signed", "unsigned", "void",
        "int", "char", "short", "long", "float", "double", "bool", "true", "false", "null", "nullptr",
        "class", "public", "private", "protected", "virtual", "override", "final", "new", "delete",
        "this", "super", "try", "catch", "finally", "throw", "throws", "interface", "extends",
        "implements", "package", "import", "abstract", "synchronized", "instanceof", "val", "var",
        "fun", "object", "when", "is", "as", "in", "out", "let", "apply", "also", "run", "with",
        "suspend", "data", "sealed", "companion", "init", "by", "where", "typealias", "lateinit",
        "vararg", "operator", "infix", "async", "await", "yield", "function", "let", "of",
    )

    private val C_TYPES = setOf(
        "String", "Int", "Long", "Double", "Float", "Boolean", "Char", "Byte", "Short", "Unit",
        "Any", "Nothing", "List", "Map", "Set", "Array", "MutableList", "Exception", "Throwable",
        "size_t", "uint8_t", "int32_t", "uint32_t", "FILE", "std", "StringBuilder", "IntArray",
    )

    private val PY_KEYWORDS = setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "break", "continue",
        "import", "from", "as", "try", "except", "finally", "raise", "with", "lambda", "global",
        "nonlocal", "pass", "yield", "assert", "del", "in", "is", "not", "and", "or", "None",
        "True", "False", "async", "await", "self",
    )

    private val PY_BUILTINS = setOf(
        "print", "len", "range", "int", "float", "str", "list", "dict", "set", "tuple", "bool",
        "sum", "min", "max", "abs", "round", "zip", "enumerate", "sorted", "open", "isinstance",
        "type", "super", "map", "filter", "any", "all",
    )

    private val SH_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
        "function", "return", "local", "export", "echo", "cd", "set", "unset", "source", "exit",
        "echo", "printf", "read", "test", "param", "foreach", "in", "begin", "process", "end",
    )

    private fun familyOf(language: String?): Family = when (language?.lowercase()?.trim()) {
        "py", "python", "python3" -> Family.PYTHON
        "sh", "bash", "shell", "zsh", "console", "ps1", "powershell", "bat", "cmd" -> Family.SHELL
        "c", "h", "cpp", "c++", "cc", "hpp", "cs", "java", "kt", "kotlin", "kts", "js",
        "javascript", "ts", "typescript", "go", "golang", "rust", "rs", "swift", "scala",
        "dart", "groovy", "gradle", "json5", "proto", "glsl", "hlsl", "m", "mm" -> Family.C_LIKE
        else -> Family.PLAIN
    }

    /** 把代码切成着色区间；返回的区间按 start 升序且互不重叠 */
    fun tokenize(code: String, language: String?): List<Token> {
        val family = familyOf(language)
        if (family == Family.PLAIN) return emptyList()

        val tokens = ArrayList<Token>()
        var i = 0
        val n = code.length

        while (i < n) {
            val c = code[i]

            // 行注释
            if (family == Family.PYTHON || family == Family.SHELL) {
                if (c == '#') {
                    val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                    tokens.add(Token(i, end, TokenKind.COMMENT))
                    i = end
                    continue
                }
            }
            if (family == Family.C_LIKE && c == '/' && i + 1 < n && code[i + 1] == '/') {
                val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                tokens.add(Token(i, end, TokenKind.COMMENT))
                i = end
                continue
            }
            // 块注释 /* ... */
            if (family == Family.C_LIKE && c == '/' && i + 1 < n && code[i + 1] == '*') {
                val end = code.indexOf("*/", i + 2).let { if (it < 0) n else it + 2 }
                tokens.add(Token(i, end, TokenKind.COMMENT))
                i = end
                continue
            }

            // 字符串
            if (c == '"' || c == '\'') {
                var j = i + 1
                while (j < n) {
                    if (code[j] == '\\') { j += 2; continue }
                    if (code[j] == c) { j++; break }
                    if (code[j] == '\n') break
                    j++
                }
                tokens.add(Token(i, minOf(j, n), TokenKind.STRING))
                i = minOf(j, n)
                continue
            }

            // 数字
            if (c.isDigit()) {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j++
                tokens.add(Token(i, j, TokenKind.NUMBER))
                i = j
                continue
            }

            // 标识符 / 关键字
            if (c.isLetter() || c == '_') {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                val kind = when {
                    family == Family.PYTHON -> when {
                        word in PY_KEYWORDS -> TokenKind.KEYWORD
                        word in PY_BUILTINS -> TokenKind.TYPE
                        word.length > 1 && word[0].isUpperCase() -> TokenKind.TYPE
                        else -> null
                    }
                    family == Family.SHELL -> if (word in SH_KEYWORDS) TokenKind.KEYWORD else null
                    else -> when {
                        word in C_KEYWORDS -> TokenKind.KEYWORD
                        word in C_TYPES -> TokenKind.TYPE
                        // 常见的"大写开头 = 类型/类"惯例
                        word.length > 1 && word[0].isUpperCase() && word.any { it.isLowerCase() } -> TokenKind.TYPE
                        else -> null
                    }
                }
                if (kind != null) tokens.add(Token(i, j, kind))
                i = j
                continue
            }

            i++
        }
        return tokens
    }
}
