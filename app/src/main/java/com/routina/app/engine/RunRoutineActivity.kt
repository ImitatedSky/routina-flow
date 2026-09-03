package com.routina.app.engine

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * 桌面捷徑的執行跳板：透明、讀完 extra 就 finish，使用者只看到動作被執行、看不到畫面。
 *
 * 由 [RunFromOutside.pinShortcut] 建立的釘選捷徑指向這裡，帶著要執行的程序 id。
 * 主題是 Theme.Routina.Invisible（透明、無動畫），與 NFC dispatch 入口同一套做法：
 * 立刻 finish，實際動作掛在 [RunFromOutside] 的進程層級 scope 上，不會被這裡的結束中斷。
 */
class RunRoutineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RunFromOutside.run(applicationContext, intent?.getStringExtra(EXTRA_ROUTINE_ID))
        finish()
        // 連結束動畫都不要，避免透明畫面在轉場中被看見
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_ROUTINE_ID = "routine_id"

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
