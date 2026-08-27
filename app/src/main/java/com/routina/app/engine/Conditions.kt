package com.routina.app.engine

import com.routina.app.model.CompareOp
import com.routina.app.model.Condition

/**
 * 流程控制判斷式的求值器。
 *
 * 左右兩邊先經 [VariableResolver] 代入實際值（可放 `{{全域:x}}`、`{{var:y}}`、字面值），
 * 再依運算子比較：
 * - 相等／不等：兩邊都能轉成數字就比數值，否則比字串（前後空白已修剪）。
 * - 大小比較：兩邊都能轉成數字才成立，否則一律 false（避免字串大小的意外行為）。
 * - 包含／不包含：字串包含。
 * - 為空／不為空：只看左邊。
 */
object ConditionEvaluator {

    fun eval(cond: Condition, ctx: RunContext): Boolean {
        val left = VariableResolver.resolve(cond.left, ctx).trim()
        return when (cond.op) {
            CompareOp.IS_EMPTY -> left.isEmpty()
            CompareOp.IS_NOT_EMPTY -> left.isNotEmpty()
            CompareOp.IS_TRUE -> isTruthy(left)
            CompareOp.IS_FALSE -> !isTruthy(left)
            else -> {
                val right = VariableResolver.resolve(cond.right, ctx).trim()
                when (cond.op) {
                    CompareOp.EQUALS -> equalsSmart(left, right)
                    CompareOp.NOT_EQUALS -> !equalsSmart(left, right)
                    CompareOp.CONTAINS -> left.contains(right)
                    CompareOp.NOT_CONTAINS -> !left.contains(right)
                    CompareOp.GREATER -> numCompare(left, right)?.let { it > 0 } ?: false
                    CompareOp.GREATER_EQUAL -> numCompare(left, right)?.let { it >= 0 } ?: false
                    CompareOp.LESS -> numCompare(left, right)?.let { it < 0 } ?: false
                    CompareOp.LESS_EQUAL -> numCompare(left, right)?.let { it <= 0 } ?: false
                    // IS_EMPTY / IS_NOT_EMPTY 已在外層處理
                    else -> false
                }
            }
        }
    }

    /** 兩邊都是數字→比數值（含 3 == 3.0）；否則比字串 */
    private fun equalsSmart(a: String, b: String): Boolean {
        val na = a.toDoubleOrNull()
        val nb = b.toDoubleOrNull()
        return if (na != null && nb != null) na == nb else a == b
    }

    /** 視為「真」的字串（設定變數存 true/1/yes/是… 即可當布林旗標） */
    private fun isTruthy(s: String): Boolean =
        s.trim().lowercase() in setOf("true", "1", "yes", "on", "y", "是", "真")

    /** 兩邊都是數字才回傳比較結果；任一邊不是數字回 null（該比較視為 false） */
    private fun numCompare(a: String, b: String): Int? {
        val na = a.toDoubleOrNull() ?: return null
        val nb = b.toDoubleOrNull() ?: return null
        return na.compareTo(nb)
    }
}
