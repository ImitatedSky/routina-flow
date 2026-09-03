package com.routina.app.engine

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.routina.app.data.RoutineRepository
import com.routina.app.model.ActionResult
import com.routina.app.model.RingerModeType
import com.routina.app.model.Routine
import com.routina.app.model.RunLog
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource
import com.routina.app.model.VolumeStream
import com.routina.app.model.opposite
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * 離開時還原（Samsung 情境模式 / Tasker exit task 的精神）。
 *
 * 開了這個選項的程序由觸發執行時，動作跑之前先拍一張「裝置設定快照」；
 * 等條件結束（反向觸發，見 [Trigger.opposite]）的事件送達時，再把快照套回去。
 *
 * 三個原則：
 * - **只記能還原的**：讀不到、或沒權限寫回去的項目在快照時就略過，還原時自然也不碰。
 * - **不是一次觸發**：還原不執行程序的動作，也不走 [RoutineExecutor.execute]，
 *   因此不計入觸發上限次數（maxRuns / runCount），只寫一筆說明用的執行紀錄。
 * - **絕不崩潰**：每一項各自成敗、各自記錄，任何例外都只影響那一項。
 *
 * 快照以 [android.content.SharedPreferences] 存放（key＝routine id、value＝JSON），
 * 行程被回收後仍在，重開機後也讀得到。
 */
object RestoreOnExit {

    /** 執行紀錄的說明文字，讓使用者在「執行紀錄」裡認得出這不是一次觸發 */
    private const val NOTE = "離開時還原設定"

    private const val PREFS = "restore_on_exit"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 觸發前的裝置設定快照。每個欄位都可能是 null／空：
     * 代表當下讀不到或還原不了（例如沒有勿擾模式存取權），還原時就跳過那一項。
     */
    @Serializable
    private data class Snapshot(
        val ringerMode: RingerModeType? = null,
        val dndOn: Boolean? = null,
        /** 各音量串流的系統原始刻度（非百分比） */
        val volumes: Map<VolumeStream, Int> = emptyMap(),
        /** 螢幕亮度的系統原始刻度（0–255） */
        val brightness: Int? = null,
        /** 自動旋轉（Settings.System.ACCELEROMETER_ROTATION：1＝開） */
        val autoRotate: Int? = null
    ) {
        val isEmpty: Boolean
            get() = ringerMode == null && dndOn == null && volumes.isEmpty() &&
                brightness == null && autoRotate == null
    }

    // ---------- 對外入口 ----------

    /**
     * 記下 [routine] 觸發當下的裝置設定（覆蓋上一張快照）。
     * 全部都讀不到時不留任何東西，之後也就沒有東西可還原。
     */
    fun snapshot(context: Context, routine: Routine) {
        val appContext = context.applicationContext
        val snapshot = Snapshot(
            ringerMode = readRingerMode(appContext),
            dndOn = readDndOn(appContext),
            volumes = readVolumes(appContext),
            brightness = readBrightness(appContext),
            autoRotate = readAutoRotate(appContext)
        )
        if (snapshot.isEmpty) return
        runCatching {
            prefs(appContext).edit()
                .putString(routine.id, json.encodeToString(Snapshot.serializer(), snapshot))
                .commit()
        }
    }

    /**
     * 把 [routine] 的快照套回去，並刪掉快照（一張快照只還原一次）。
     * 沒有快照（沒觸發過、或已經還原過）時什麼都不做。
     */
    fun restore(context: Context, routine: Routine) {
        val appContext = context.applicationContext
        val snapshot = read(appContext, routine.id) ?: return
        clear(appContext, routine.id)

        val results = mutableListOf<ActionResult>()
        snapshot.ringerMode?.let { mode ->
            apply(results, "響鈴模式：${RoutineExecutor.ringerLabel(mode)}") {
                RoutineExecutor.applyRingerMode(appContext, mode)
            }
        }
        snapshot.volumes.forEach { (stream, index) ->
            val label = RoutineExecutor.volumeStreamLabel(stream)
            apply(results, "${label}音量：${volumeText(appContext, stream, index)}") {
                RoutineExecutor.applyStreamVolume(appContext, stream, index)
            }
        }
        snapshot.dndOn?.let { on ->
            apply(results, "勿擾模式：${if (on) "開啟" else "關閉"}") {
                RoutineExecutor.applyDnd(appContext, on)
            }
        }
        snapshot.brightness?.let { value ->
            val percent = (value * 100f / RoutineExecutor.MAX_BRIGHTNESS).roundToInt()
            apply(results, "螢幕亮度：$percent%") {
                RoutineExecutor.applyBrightness(appContext, value)
            }
        }
        snapshot.autoRotate?.let { value ->
            apply(results, "自動旋轉：${if (value == 1) "開啟" else "關閉"}") {
                writeAutoRotate(appContext, value)
            }
        }
        if (results.isEmpty()) return

        // 背景元件呼叫進來的居多，紀錄同步落地才不會在行程被回收時漏掉
        RoutineRepository.get(appContext).addLogBlocking(
            RunLog(
                routineId = routine.id,
                routineName = routine.name,
                source = TriggerSource.SYSTEM,
                results = results,
                note = NOTE
            )
        )
    }

    /**
     * 事件送達時的統一入口：把開了「離開時還原」、且反向觸發對得上這次事件的程序還原回去。
     *
     * [matches] 直接沿用分派點原本用來篩選 routine 的比對——差別只在比對的對象換成
     * [Trigger.opposite]，因此不必為「條件結束」另外寫一套比對規則。
     */
    fun onEvent(context: Context, matches: (Trigger) -> Boolean) {
        onEvent(context, RoutineRepository.get(context).routines.value, matches)
    }

    /** 事件只涉及特定幾個程序時（例如地理圍欄以 routine id 回報）用這個版本 */
    fun onEvent(context: Context, routines: List<Routine>, matches: (Trigger) -> Boolean) {
        routines.filter { routine ->
            routine.enabled && routine.restoreOnExit &&
                routine.trigger.opposite?.let(matches) == true
        }.forEach { routine ->
            runCatching { restore(context, routine) }
        }
    }

    // ---------- 讀取現況（讀不到就略過，不記進快照）----------

    /**
     * 響鈴模式。靜音 / 震動要寫回去需要勿擾模式存取權，沒授權就不記
     * （記了也還原不了，只會在紀錄裡留下一筆必然失敗）。
     */
    private fun readRingerMode(context: Context): RingerModeType? = runCatching {
        val mode = when (context.getSystemService(AudioManager::class.java)?.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> RingerModeType.NORMAL
            AudioManager.RINGER_MODE_VIBRATE -> RingerModeType.VIBRATE
            AudioManager.RINGER_MODE_SILENT -> RingerModeType.SILENT
            else -> null
        }
        if (mode != null && mode != RingerModeType.NORMAL && !hasDndAccess(context)) null else mode
    }.getOrNull()

    /** 勿擾模式：沒有勿擾模式存取權就既讀不準也寫不回去 → 不記 */
    private fun readDndOn(context: Context): Boolean? = runCatching {
        if (!hasDndAccess(context)) return@runCatching null
        val filter = context.getSystemService(NotificationManager::class.java)
            ?.currentInterruptionFilter ?: return@runCatching null
        filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }.getOrNull()

    private fun readVolumes(context: Context): Map<VolumeStream, Int> = runCatching {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: return@runCatching emptyMap()
        VolumeStream.entries.mapNotNull { stream ->
            runCatching {
                stream to audio.getStreamVolume(RoutineExecutor.audioStream(stream))
            }.getOrNull()
        }.toMap()
    }.getOrDefault(emptyMap())

    /** 螢幕亮度：沒有「修改系統設定」權限就寫不回去 → 不記 */
    private fun readBrightness(context: Context): Int? = runCatching {
        if (!RoutineExecutor.canWriteSettings(context)) return@runCatching null
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrNull()

    /** 自動旋轉：同樣需要「修改系統設定」權限 */
    private fun readAutoRotate(context: Context): Int? = runCatching {
        if (!RoutineExecutor.canWriteSettings(context)) return@runCatching null
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION)
    }.getOrNull()

    private fun hasDndAccess(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
    }.getOrDefault(false)

    // ---------- 套用 ----------

    /** 自動旋轉沒有對應的動作可共用，這裡自己寫回去（權限前提同螢幕亮度） */
    private fun writeAutoRotate(context: Context, value: Int) {
        if (!RoutineExecutor.canWriteSettings(context)) {
            error("缺少「修改系統設定」權限")
        }
        val written = Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            if (value == 1) 1 else 0
        )
        if (!written) error("系統拒絕寫入自動旋轉設定")
    }

    /** 套用一項設定並記一筆結果；某一項失敗不影響其他項目 */
    private fun apply(results: MutableList<ActionResult>, description: String, block: () -> Unit) {
        results += try {
            block()
            ActionResult(description, true)
        } catch (t: Throwable) {
            ActionResult(description, false, t.message ?: t.javaClass.simpleName)
        }
    }

    /** 紀錄顯示用：把原始刻度換成百分比（讀不到最大值就直接顯示刻度） */
    private fun volumeText(context: Context, stream: VolumeStream, index: Int): String {
        val max = runCatching {
            context.getSystemService(AudioManager::class.java)
                ?.getStreamMaxVolume(RoutineExecutor.audioStream(stream)) ?: 0
        }.getOrDefault(0)
        return if (max > 0) "${(index * 100f / max).roundToInt()}%" else index.toString()
    }

    // ---------- 快照存取 ----------

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context, routineId: String): Snapshot? = runCatching {
        val raw = prefs(context).getString(routineId, null) ?: return null
        json.decodeFromString(Snapshot.serializer(), raw)
    }.getOrNull()

    private fun clear(context: Context, routineId: String) {
        runCatching { prefs(context).edit().remove(routineId).commit() }
    }
}
