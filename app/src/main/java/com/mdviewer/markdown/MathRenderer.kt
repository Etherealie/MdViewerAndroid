package com.mdviewer.markdown

/**
 * 轻量 LaTeX 公式转换器。
 *
 * 不做真正的排版，而是把常见 LaTeX 记法转成「能看懂的文本 + 样式」：
 *   - 希腊字母、运算符、箭头等映射成 Unicode（\alpha -> α，\times -> ×）
 *   - `_x` / `^x` / `_{...}` / `^{...}` 标成下标 / 上标样式（渲染层用小字号 + 基线偏移）
 *   - `\frac{a}{b}` -> `(a)/(b)`，`\sqrt{x}` -> `√(x)`
 *   - `\bar{x}` / `\hat{x}` / `\vec{x}` 用组合字符叠加上划线等
 *   - `\text{...}` / `\mathrm{...}` 原样输出，`\left` `\right` 之类直接丢掉
 *
 * 纯 Kotlin、不依赖 Android，所以能直接跑 JVM 单元测试。
 */
object MathRenderer {

    fun render(latex: String): InlineText {
        val out = StringBuilder()
        val spans = ArrayList<InlineSpan>()
        emit(latex, out, spans, null)
        return InlineText(out.toString(), spans)
    }

    /** 只要文本，不要样式 */
    fun plain(latex: String): String = render(latex).text

    // ------------------------------------------------------------------

    private val SYMBOLS: Map<String, String> = buildMap {
        // 小写希腊字母
        put("alpha", "α"); put("beta", "β"); put("gamma", "γ"); put("delta", "δ")
        put("epsilon", "ε"); put("varepsilon", "ε"); put("zeta", "ζ"); put("eta", "η")
        put("theta", "θ"); put("vartheta", "ϑ"); put("iota", "ι"); put("kappa", "κ")
        put("lambda", "λ"); put("mu", "μ"); put("nu", "ν"); put("xi", "ξ")
        put("pi", "π"); put("varpi", "ϖ"); put("rho", "ρ"); put("varrho", "ϱ")
        put("sigma", "σ"); put("varsigma", "ς"); put("tau", "τ"); put("upsilon", "υ")
        put("phi", "φ"); put("varphi", "φ"); put("chi", "χ"); put("psi", "ψ"); put("omega", "ω")
        // 大写希腊字母
        put("Gamma", "Γ"); put("Delta", "Δ"); put("Theta", "Θ"); put("Lambda", "Λ")
        put("Xi", "Ξ"); put("Pi", "Π"); put("Sigma", "Σ"); put("Upsilon", "Υ")
        put("Phi", "Φ"); put("Psi", "Ψ"); put("Omega", "Ω")
        // 运算
        put("times", "×"); put("div", "÷"); put("cdot", "·"); put("pm", "±")
        put("mp", "∓"); put("ast", "∗"); put("star", "⋆"); put("circ", "∘")
        put("bullet", "•"); put("oplus", "⊕"); put("otimes", "⊗"); put("odot", "⊙")
        // 关系
        put("leq", "≤"); put("le", "≤"); put("geq", "≥"); put("ge", "≥")
        put("neq", "≠"); put("ne", "≠"); put("approx", "≈"); put("equiv", "≡")
        put("sim", "∼"); put("simeq", "≃"); put("cong", "≅"); put("propto", "∝")
        put("ll", "≪"); put("gg", "≫"); put("doteq", "≐"); put("asymp", "≍")
        put("subset", "⊂"); put("subseteq", "⊆"); put("supset", "⊃"); put("supseteq", "⊇")
        put("in", "∈"); put("notin", "∉"); put("ni", "∋"); put("emptyset", "∅")
        put("perp", "⊥"); put("parallel", "∥")
        // 大算符与其它
        put("sum", "∑"); put("prod", "∏"); put("int", "∫"); put("oint", "∮")
        put("partial", "∂"); put("nabla", "∇"); put("infty", "∞"); put("sqrt", "√")
        put("angle", "∠"); put("degree", "°"); put("triangle", "△")
        put("cup", "∪"); put("cap", "∩"); put("setminus", "∖")
        put("forall", "∀"); put("exists", "∃"); put("neg", "¬"); put("lnot", "¬")
        put("land", "∧"); put("wedge", "∧"); put("lor", "∨"); put("vee", "∨")
        put("therefore", "∴"); put("because", "∵")
        // 箭头
        put("to", "→"); put("rightarrow", "→"); put("leftarrow", "←")
        put("Rightarrow", "⇒"); put("Leftarrow", "⇐"); put("leftrightarrow", "↔")
        put("Leftrightarrow", "⇔"); put("uparrow", "↑"); put("downarrow", "↓")
        put("mapsto", "↦"); put("implies", "⟹"); put("iff", "⟺")
        // 省略号与特殊符号
        put("ldots", "…"); put("dots", "…"); put("cdots", "⋯")
        put("vdots", "⋮"); put("ddots", "⋱"); put("dotsb", "⋯")
        put("hbar", "ℏ"); put("ell", "ℓ"); put("aleph", "ℵ")
        put("prime", "′"); put("checkmark", "✓")
    }

    /** 间距命令，转成一个空格 */
    private val SPACING = setOf(
        ",", ";", ":", "!", " ", "quad", "qquad", "enspace", "thinspace", "medspace", "thickspace",
    )

    /** 只影响排版、直接丢掉的命令 */
    private val DROPPED = setOf(
        "left", "right", "displaystyle", "textstyle", "limits", "nolimits",
        "big", "Big", "bigg", "Bigg", "bigl", "bigr", "biggl", "biggr", "!",
    )

    /** 取参数后按原文照抄（不再解析下划线/上标） */
    private val LITERAL_ARG = setOf("text", "mathrm", "operatorname", "mbox", "textbf", "textit")

    /** 组合附加符号：作用于后面的参数 */
    private val COMBINING = mapOf(
        "bar" to "\u0304", "overline" to "\u0305", "hat" to "\u0302",
        "tilde" to "\u0303", "dot" to "\u0307", "ddot" to "\u0308",
        "vec" to "\u20D7", "acute" to "\u0301", "grave" to "\u0300",
    )

    /** 这些命令后面跟一个 {} 参数，整段忽略 */
    private val SKIP_WITH_ARG = setOf("begin", "end", "label", "tag", "hspace", "vspace", "phantom")

    // ------------------------------------------------------------------

    private fun emit(
        src: String,
        out: StringBuilder,
        spans: MutableList<InlineSpan>,
        style: InlineStyle?,
    ) {
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                c == '\\' -> {
                    var j = i + 1
                    while (j < src.length && src[j].isLetter()) j++
                    val name = src.substring(i + 1, j)

                    if (name.isEmpty()) {
                        // \, \; \! \{ \} \% 之类
                        val sym = if (i + 1 < src.length) src[i + 1].toString() else ""
                        when (sym) {
                            ",", ";", ":", "!", " ", "\\" -> append(out, spans, " ", style)
                            else -> append(out, spans, sym, style)
                        }
                        i += 2
                        continue
                    }

                    if (name in SPACING) {
                        append(out, spans, " ", style)
                        i = j
                        continue
                    }
                    if (name in DROPPED) {
                        i = j
                        continue
                    }
                    if (name in SKIP_WITH_ARG) {
                        val (_, next) = argument(src, j)
                        i = next
                        continue
                    }
                    if (name in LITERAL_ARG) {
                        val (arg, next) = argument(src, j)
                        append(out, spans, arg, style)
                        i = next
                        continue
                    }
                    if (name in COMBINING) {
                        val (arg, next) = argument(src, j)
                        emit(arg, out, spans, style)
                        append(out, spans, COMBINING.getValue(name), style)
                        i = next
                        continue
                    }
                    when (name) {
                        "frac", "dfrac", "tfrac" -> {
                            val (a, n1) = argument(src, j)
                            val (b, n2) = argument(src, n1)
                            emit(wrap(a), out, spans, style)
                            append(out, spans, "/", style)
                            emit(wrap(b), out, spans, style)
                            i = n2
                            continue
                        }
                        "sqrt" -> {
                            var k = j
                            var root = ""
                            if (k < src.length && src[k] == '[') {
                                val close = src.indexOf(']', k)
                                if (close > k) {
                                    root = src.substring(k + 1, close).trim()
                                    k = close + 1
                                }
                            }
                            val (a, next) = argument(src, k)
                            append(out, spans, "√", style)
                            if (root.isNotEmpty() && root != "2") append(out, spans, "[$root]", style)
                            emit(wrap(a), out, spans, style)
                            i = next
                            continue
                        }
                    }
                    val symbol = SYMBOLS[name]
                    if (symbol != null) {
                        append(out, spans, symbol, style)
                    } else {
                        // 不认识的命令：把名字留着，总比整段消失好
                        append(out, spans, name, style)
                    }
                    i = j
                }

                // 上标：^{...} 或 ^x
                c == '^' -> {
                    val (arg, next) = argument(src, i + 1)
                    emit(arg, out, spans, InlineStyle.SUPER)
                    i = next
                }

                // 下标：_{...} 或 _x
                c == '_' -> {
                    val (arg, next) = argument(src, i + 1)
                    emit(arg, out, spans, InlineStyle.SUB)
                    i = next
                }

                // 花括号只是分组
                c == '{' || c == '}' -> i++

                c == '~' -> {
                    append(out, spans, " ", style)
                    i++
                }

                c == '&' -> {
                    append(out, spans, " ", style)
                    i++
                }

                else -> {
                    append(out, spans, c.toString(), style)
                    i++
                }
            }
        }
    }

    /** 取出一个参数：`{...}`（支持嵌套）或单个字符/单条命令 */
    private fun argument(src: String, at: Int): Pair<String, Int> {
        if (at >= src.length) return "" to at
        if (src[at] == '{') {
            var depth = 0
            var i = at
            while (i < src.length) {
                when (src[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return src.substring(at + 1, i) to (i + 1)
                    }
                }
                i++
            }
            return src.substring(at + 1) to src.length
        }
        if (src[at] == '\\') {
            var j = at + 1
            while (j < src.length && src[j].isLetter()) j++
            if (j == at + 1) return src.substring(at, at + 2) to (at + 2)
            return src.substring(at, j) to j
        }
        return src[at].toString() to (at + 1)
    }

    /** 多字符参数加括号，单字符不加：\frac{C_L}{2} -> (C_L)/2 */
    private fun wrap(arg: String): String =
        if (arg.trim().length <= 1) arg.trim() else "(${arg.trim()})"

    private fun append(
        out: StringBuilder,
        spans: MutableList<InlineSpan>,
        text: String,
        style: InlineStyle?,
    ) {
        if (text.isEmpty()) return
        val start = out.length
        out.append(text)
        if (style != null) spans.add(InlineSpan(start, out.length, style))
    }
}
