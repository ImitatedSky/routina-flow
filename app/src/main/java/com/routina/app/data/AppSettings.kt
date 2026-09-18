package com.routina.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 外觀模式：跟隨系統或手動指定 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * 使用者偏好的唯一來源。
 *
 * 之前偏好散在六個不同的 SharedPreferences 檔名裡，設定畫面若直接四處伸手會變成散彈；
 * 這裡只收「使用者會想調的東西」。
 *
 * 刻意不收進來的是內部狀態——離開時還原的快照、小工具綁定的程序、快速設定磚指定的程序。
 * 那些是程式自己記的帳，使用者不該看到，也不該能手動改。
 */
object AppSettings {

    private const val PREFS = "routina_settings"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_RUN_TOAST = "run_toast"
    private const val KEY_LOG_LIMIT = "log_limit"

    /** 紀錄保留上限的可選值；上限有意不做成無限，紀錄只用來回頭查一兩件事 */
    val LOG_LIMIT_CHOICES = listOf(20, 50, 100, 200)
    const val DEFAULT_LOG_LIMIT = 50

    // Compose 讀得到的狀態：設定改動後畫面要立刻跟著變，不必重開 App
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var runToast by mutableStateOf(true)
        private set
    var logLimit by mutableStateOf(DEFAULT_LOG_LIMIT)
        private set

    private var loaded = false

    /** App 啟動時載入一次；重複呼叫安全 */
    fun load(context: Context) {
        if (loaded) return
        val p = prefs(context)
        themeMode = runCatching { ThemeMode.valueOf(p.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        runToast = p.getBoolean(KEY_RUN_TOAST, true)
        logLimit = p.getInt(KEY_LOG_LIMIT, DEFAULT_LOG_LIMIT)
        loaded = true
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        themeMode = mode
        edit(context) { putString(KEY_THEME, mode.name) }
    }

    fun setRunToast(context: Context, on: Boolean) {
        runToast = on
        edit(context) { putBoolean(KEY_RUN_TOAST, on) }
    }

    fun setLogLimit(context: Context, limit: Int) {
        logLimit = limit
        edit(context) { putInt(KEY_LOG_LIMIT, limit) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // 用 commit() 同步寫，不用 apply()：偏好是使用者點一下就改一項，改完可能馬上切走、
    // 被系統收掉或裝新版（apply 的非同步寫入曾經就這樣掉過一次）。檔案只有三個鍵，
    // 主執行緒同步寫的代價可以忽略。寫入失敗不該讓 App 掛掉——頂多是回到預設值。
    private inline fun edit(
        context: Context,
        crossinline block: android.content.SharedPreferences.Editor.() -> Unit
    ) {
        runCatching {
            val editor = prefs(context).edit()
            editor.block()
            editor.commit()
        }
    }
}
