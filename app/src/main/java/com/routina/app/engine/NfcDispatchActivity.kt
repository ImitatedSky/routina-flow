package com.routina.app.engine

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Routine
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource

/**
 * NFC 標籤掃描的背景入口。
 *
 * 系統只會把 NFC dispatch 送給 Activity，所以這裡用一個全透明、讀完就結束的 Activity
 * 當收件人：比對 UID → 交給 [TriggerDispatch] → 立刻 [finish]。
 * 主題是 `Theme.Routina.Invisible`（透明、無動畫），使用者不會看到任何畫面閃動，
 * 只會看到例行程序的動作被執行。
 *
 * 兩種標籤都由這裡接手，且走同一條 UID 比對路徑（見 [NfcTagReader.uidFrom]）：
 * 寫入過專屬 URI 的標籤由系統以 `routina://tag/{uid}` 精準比對送來
 * （只有 Routina 符合 → 不出現 App 選擇器），UID 直接取自 intent 的 data URI；
 * 其餘標籤照舊從夾帶的 Tag 讀硬體 UID。
 *
 * 未登錄的標籤只顯示一則 Toast，不執行任何程序、也不開啟 App。
 */
class NfcDispatchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleTag(intent)
        finish()
        // 連結束動畫都不要，避免透明畫面在轉場中被看見
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    /** launchMode=singleTop：連續掃描時同一個實例會收到新的 intent */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleTag(intent)
        finish()
    }

    private fun handleTag(intent: Intent?) {
        // 背景觸發的入口一律不得讓 App 崩潰
        runCatching {
            val uid = NfcTagReader.uidFrom(intent) ?: return
            val matched = matchingRoutines(uid)
            if (matched.isEmpty()) {
                Toast.makeText(this, UNKNOWN_TAG, Toast.LENGTH_SHORT).show()
                return
            }
            TriggerDispatch.run(applicationContext, matched, TriggerSource.NFC)
        }
    }

    private fun matchingRoutines(uid: String): List<Routine> =
        RoutineRepository.get(this).routines.value.filter { routine ->
            val trigger = routine.trigger
            routine.enabled &&
                trigger is Trigger.NfcTag &&
                NfcTagReader.matches(trigger.uid, uid)
        }

    private companion object {
        const val UNKNOWN_TAG = "這張 NFC 標籤還沒登錄到任何例行程序"
    }
}
