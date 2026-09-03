package com.routina.app.engine

import android.content.Context
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.routina.app.R
import com.routina.app.data.RoutineRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 「從 App 外執行例行程序」的共用邏輯：快速設定磚與桌面捷徑都走這裡。
 *
 * 兩個入口（[RunRoutineTileService]、[RunRoutineActivity]）都會在啟動執行後立刻結束，
 * 不能用它們的生命週期 scope（會把協程一起取消），因此執行掛在一個不綁定任何元件的
 * 進程層級 scope 上，撐到動作跑完。點一下磚／捷徑屬於使用者主動操作 → 一律以 MANUAL 執行
 * （與畫面上的 ▶ 相同：不受啟用狀態／觸發上限限制，也不計入觸發次數）。
 */
object RunFromOutside {

    /** 執行用 scope：刻意獨立於任何元件生命週期，跳板結束後仍能把動作做完 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 快速設定磚要執行哪個程序：id 存這裡（唯一來源）。
    // 磚讀它決定標籤與點擊行為，EditScreen 的「設為快速設定磚」寫它。
    private const val PREFS = "routina_run_outside"
    private const val KEY_TILE_ROUTINE_ID = "tile_routine_id"

    fun setTileRoutineId(context: Context, routineId: String) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TILE_ROUTINE_ID, routineId)
                .apply()
        }
    }

    fun tileRoutineId(context: Context): String? = runCatching {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TILE_ROUTINE_ID, null)
    }.getOrNull()

    /** 磚的標籤：指定程序的名稱；未指定或已被刪除時回 null（磚改用預設 App 名稱） */
    fun tileRoutineName(context: Context): String? {
        val id = tileRoutineId(context) ?: return null
        val routine = RoutineRepository.get(context).findById(id) ?: return null
        return routine.name.ifBlank { null }
    }

    /**
     * 執行 [routineId] 指定的程序，並在（呼叫端的）主執行緒上以 Toast 回饋。
     * id 為空或程序已不存在時只提示、不執行——這是 App 外的入口，一律不得崩潰。
     */
    fun run(context: Context, routineId: String?) {
        val appContext = context.applicationContext
        if (routineId.isNullOrBlank()) {
            toast(appContext, MSG_NOT_CONFIGURED)
            return
        }
        val routine = RoutineRepository.get(appContext).findById(routineId)
        if (routine == null) {
            toast(appContext, MSG_MISSING)
            return
        }
        toast(appContext, "執行：${routine.name.ifBlank { "未命名" }}")
        scope.launch {
            runCatching { RoutineManager.runNow(appContext, routineId) }
        }
    }

    /**
     * 要求系統把 [routineId] 釘到桌面：捷徑點擊時經 [RunRoutineActivity] 執行該程序。
     * @return 是否已送出釘選請求；桌面不支援釘選時回 false，由呼叫端提示使用者。
     */
    fun pinShortcut(context: Context, routineId: String, routineName: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val label = routineName.ifBlank { context.getString(R.string.app_name) }
        val shortcut = ShortcutInfoCompat.Builder(context, "routine_$routineId")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(RunRoutineActivity.intent(context, routineId))
            .build()
        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)
    }

    /** 兩個入口都在主執行緒呼叫（Activity.onCreate／TileService.onClick），可直接顯示 */
    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    const val MSG_NOT_CONFIGURED = "還沒指定要執行的例行程序，請在程式的例行程序選單中設定"
    const val MSG_MISSING = "找不到這個例行程序，可能已被刪除"
    const val MSG_PIN_UNSUPPORTED = "這個裝置的桌面不支援釘選捷徑"
}
