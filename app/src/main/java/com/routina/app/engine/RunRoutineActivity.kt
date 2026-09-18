package com.routina.app.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * 「從 App 外執行程序」的跳板：透明、讀完 extra 就 finish，使用者只看到動作被執行、看不到畫面。
 *
 * 兩種來源共用這一個入口：
 * - [RunFromOutside.pinShortcut] 建立的桌面捷徑（顯式元件 + [EXTRA_ROUTINE_ID]）
 * - 家族成員呼叫 `run_routine` 能力（[FamilyLink.ACTION_RUN_CAPABILITY] + 參數 `routine_id`）
 *
 * 主題是 Theme.Routina.Invisible（透明、無動畫），與 NFC dispatch 入口同一套做法：
 * 立刻 finish，實際動作掛在 [RunFromOutside] 的進程層級 scope 上，不會被這裡的結束中斷。
 */
class RunRoutineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RunFromOutside.run(applicationContext, requestedRoutineId(intent))
        finish()
        // 連結束動畫都不要，避免透明畫面在轉場中被看見
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    /**
     * 這次要執行哪支程序。桌面捷徑帶的是自家 extra；家族呼叫帶的是契約格式的參數。
     * 家族呼叫要求能力 id 相符 —— 收到不認得的能力就當沒帶 id（由 RunFromOutside 誠實提示）。
     */
    private fun requestedRoutineId(intent: Intent?): String? {
        intent?.getStringExtra(EXTRA_ROUTINE_ID)?.takeIf { it.isNotBlank() }?.let { return it }
        if (intent?.action != FamilyLink.ACTION_RUN_CAPABILITY) return null
        if (FamilyLink.capabilityId(intent) != CAPABILITY_RUN_ROUTINE) return null
        return FamilyLink.param(intent, PARAM_ROUTINE_ID)
    }

    companion object {
        const val EXTRA_ROUTINE_ID = "routine_id"

        /** 與 res/xml/family_capabilities.xml 裡的宣告對應 */
        const val CAPABILITY_RUN_ROUTINE = "run_routine"
        const val PARAM_ROUTINE_ID = "routine_id"

        /**
         * 釘選捷徑用的啟動 intent。requestPinShortcut 要求捷徑 intent 必須帶 action，
         * 因此明確設上 ACTION_VIEW（元件已顯式指定，action 只為滿足這項要求）。
         */
        fun intent(context: Context, routineId: String): Intent =
            Intent(context, RunRoutineActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_ROUTINE_ID, routineId)
    }
}
