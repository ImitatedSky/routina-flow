package com.routina.app.engine

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * 「從 JSON 取值」動作的路徑讀取：以 `data.items[0].name` 這類點／中括號路徑，
 * 從一段 JSON 文字取出一個值。
 *
 * 用 Android 內建的 org.json，不引進額外依賴。路徑不合語法、鍵不存在、索引超界、
 * 中途型別不符（拿物件當陣列用）一律回傳 null——呼叫端只需要「取不到」這一個結果，
 * 分辨是哪一種對使用者也沒有幫助。
 */
object JsonPath {

    /** 路徑上的一段：物件的鍵，或陣列的索引 */
    private sealed class Step {
        data class Key(val name: String) : Step()
        data class Index(val at: Int) : Step()
    }

    /** @return 取到的 JSON 值（JSONObject / JSONArray / String / 數字 / Boolean）；取不到為 null */
    fun read(json: String, path: String): Any? {
        val steps = parse(path) ?: return null
        var current: Any? = runCatching { JSONTokener(json.trim()).nextValue() }.getOrNull()
        for (step in steps) {
            val next = when {
                current is JSONObject && step is Step.Key -> current.opt(step.name)
                current is JSONArray && step is Step.Index -> current.opt(step.at)
                else -> null
            }
            if (next == null || next == JSONObject.NULL) return null
            current = next
        }
        return current
    }

    /**
     * 拆解路徑：`data.items[0].name` → Key(data)、Key(items)、Index(0)、Key(name)。
     * 空段（多餘的點、開頭就是 `[0]` 的陣列根）直接略過；不合語法或空路徑回傳 null。
     */
    private fun parse(path: String): List<Step>? {
        val steps = mutableListOf<Step>()
        for (raw in path.trim().split('.')) {
            val segment = raw.trim()
            if (segment.isEmpty()) continue
            val match = SEGMENT.matchEntire(segment) ?: return null
            val key = match.groupValues[1]
            if (key.isNotEmpty()) steps += Step.Key(key)
            for (index in INDEX.findAll(match.groupValues[2])) {
                steps += Step.Index(index.groupValues[1].toIntOrNull() ?: return null)
            }
        }
        return steps.takeIf { it.isNotEmpty() }
    }

    /** 一段路徑＝鍵名（可空）＋零到多組 `[數字]` */
    private val SEGMENT = Regex("^([^\\[\\]]*)((?:\\[\\d+])*)$")

    private val INDEX = Regex("\\[(\\d+)]")
}
