package com.routina.app.engine

/**
 * 「運算式」動作的求值：解析一行 `名稱 = 值`，回傳 (變數名稱, 要存的值)。
 *
 * 等號右邊先代入 `{{...}}` token；若含算術運算子（+ - * / %）或本身是純數字，就當算術求值
 * （變數直接寫名字，缺的當 0，有值但非數字則整段退回當文字）；否則整段當文字存起來。
 * 用引號 `"..."` 可強制當文字。整數結果不留小數。
 */
object ExpressionEval {

    /** @throws IllegalStateException 訊息可直接顯示（缺等號 / 左邊沒名稱） */
    fun evaluate(text: String, ctx: RunContext): Pair<String, String> {
        val eq = text.indexOf('=')
        if (eq < 0) error("格式應為「變數 = 值」，少了等號")
        val name = text.substring(0, eq).trim()
        if (name.isBlank()) error("等號左邊要有變數名稱")
        val rhsRaw = text.substring(eq + 1).trim()

        // 引號字串 → 強制當文字（去掉引號，內部仍代入 {{...}}）
        if (rhsRaw.length >= 2 && rhsRaw.startsWith("\"") && rhsRaw.endsWith("\"")) {
            return name to VariableResolver.resolve(rhsRaw.substring(1, rhsRaw.length - 1), ctx)
        }

        // 先代入 {{...}} token，維持既有語法相容
        val rhs = VariableResolver.resolve(rhsRaw, ctx)

        // 有算術運算子（或純數字）才嘗試數學；成功就用數字結果，否則整段當文字
        val numeric = if (hasMathOperator(rhs) || rhs.toDoubleOrNull() != null) {
            runCatching { Evaluator(rhs, ctx).parseAll() }.getOrNull()
        } else {
            null
        }
        return name to (numeric?.let { formatNumber(it) } ?: rhs)
    }

    /** 是否含頂層算術運算子（數字前的正負號不算） */
    private fun hasMathOperator(s: String): Boolean {
        var prevWasValue = false
        for (c in s) {
            when {
                c.isWhitespace() -> {}
                c == '+' || c == '*' || c == '/' || c == '%' -> return true
                c == '-' -> if (prevWasValue) return true
                c == '(' -> prevWasValue = false
                c == ')' -> prevWasValue = true
                else -> prevWasValue = true
            }
        }
        return false
    }

    private fun formatNumber(d: Double): String = when {
        d.isNaN() || d.isInfinite() -> "0"
        d % 1.0 == 0.0 -> d.toLong().toString()
        else -> String.format(java.util.Locale.US, "%.6f", d).trimEnd('0').trimEnd('.')
    }

    /**
     * 遞迴下降求值器：
     * expr = term ((+|-) term)*；term = factor ((*|/|%) factor)*；
     * factor = 數字 | 變數 | (expr) | ±factor。解析後若有殘留字元代表非純算術，交外層當文字。
     */
    private class Evaluator(private val s: String, private val ctx: RunContext) {
        private var pos = 0

        fun parseAll(): Double {
            val v = parseExpr()
            skipWs()
            if (pos != s.length) error("非純算術")
            return v
        }

        private fun parseExpr(): Double {
            var v = parseTerm()
            while (true) {
                skipWs()
                when (peek()) {
                    '+' -> { pos++; v += parseTerm() }
                    '-' -> { pos++; v -= parseTerm() }
                    else -> break
                }
            }
            return v
        }

        private fun parseTerm(): Double {
            var v = parseFactor()
            while (true) {
                skipWs()
                when (peek()) {
                    '*' -> { pos++; v *= parseFactor() }
                    '/' -> { pos++; val d = parseFactor(); if (d == 0.0) error("除以 0"); v /= d }
                    '%' -> { pos++; val d = parseFactor(); if (d == 0.0) error("對 0 取餘"); v %= d }
                    else -> break
                }
            }
            return v
        }

        private fun parseFactor(): Double {
            skipWs()
            when (peek() ?: error("式子不完整")) {
                '-' -> { pos++; return -parseFactor() }
                '+' -> { pos++; return parseFactor() }
                '(' -> {
                    pos++
                    val v = parseExpr()
                    skipWs()
                    if (peek() != ')') error("缺右括號")
                    pos++
                    return v
                }
            }
            val c = peek()!!
            return if (c.isDigit() || c == '.') parseNumber() else parseIdentifier()
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            return s.substring(start, pos).toDoubleOrNull() ?: error("數字格式錯誤")
        }

        private fun parseIdentifier(): Double {
            val start = pos
            while (pos < s.length && (s[pos].isLetterOrDigit() || s[pos] == '_')) pos++
            if (pos == start) error("看不懂的符號")
            val id = s.substring(start, pos)
            val raw = ctx.vars[id] ?: ctx.globals[id] ?: ctx.trigger[id]
            // 沒設過的變數當 0（方便 count = count + 1 首次執行）；有值但非數字 → 整段不算術
            return raw?.trim()?.toDoubleOrNull() ?: if (raw == null) 0.0 else error("變數「$id」不是數字")
        }

        private fun peek(): Char? = if (pos < s.length) s[pos] else null
        private fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }
    }
}
