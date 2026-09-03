package com.routina.app.engine

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.routina.app.model.RingerModeType
import com.routina.app.model.VolumeStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * 「記住目前設定 / 回復設定」共用的快照存取。
 *
 * 只負責兩件事：把當下可讀的裝置設定拍成一張快照存進 SharedPreferences（[capture]），
 * 以及把它讀回來（[load]）。真正把值寫回系統的動作留給 [RoutineExecutor] 沿用既有的
 * doMediaVolume / doRingerMode / doDnd / doBrightness——這裡不重複那套權限與降級邏輯。
 *
 * 快照只有一張（單一 slot），後拍的覆蓋前一張，符合「記住 → 回復」的直覺。
 * 存成 JSON、行程被回收後仍在。讀不到或沒授權的欄位以 null 表示「沒擷取」，回復時自然跳過。
 */
object SettingsSnapshot {

    private const val PREFS = "settings_snapshot"
    private const val KEY = "snapshot"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 一張裝置設定快照。音量與亮度都存成百分比（0–100），回復時直接餵給既有動作的 percent 參數。
     * 每個欄位可能為 null／空：代表拍照當下讀不到或沒授權，回復時略過該項。
     */
    @Serializable
    data class Snapshot(
        /** 各串流音量（百分比 0–100） */
        val volumes: Map<VolumeStream, Int> = emptyMap(),
        val ringerMode: RingerModeType? = null,
        /** 勿擾是否開啟；null＝沒有勿擾模式存取權，未擷取 */
        val dndOn: Boolean? = null,
        /** 螢幕亮度（百分比 0–100）；null＝讀不到 */
        val brightnessPercent: Int? = null,
        /** 亮度是否為自動模式；null＝讀不到 */
        val brightnessAuto: Boolean? = null,
        val capturedAt: Long = System.currentTimeMillis()
    )

    /**
     * 讀取當下可讀的設定並存成快照（覆蓋前一張）。
     * @return 對略過欄位的說明（例如未擷取勿擾）；全部擷取到時回 null。
     */
    fun capture(context: Context): String? {
        val appContext = context.applicationContext
        val audio = appContext.getSystemService(AudioManager::class.java)

        val volumes = if (audio == null) emptyMap() else VolumeStream.entries.mapNotNull { stream ->
            runCatching {
                val streamType = audioStream(stream)
                val max = audio.getStreamMaxVolume(streamType)
                if (max <= 0) return@runCatching null
                val percent = (audio.getStreamVolume(streamType) * 100f / max).roundToInt()
                stream to percent.coerceIn(0, 100)
            }.getOrNull()
        }.toMap()

        val ringerMode = when (audio?.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> RingerModeType.NORMAL
            AudioManager.RINGER_MODE_VIBRATE -> RingerModeType.VIBRATE
            AudioManager.RINGER_MODE_SILENT -> RingerModeType.SILENT
            else -> null
        }

        // 勿擾：只有取得勿擾模式存取權才讀得準、也才寫得回去
        val dndOn = if (!hasDndAccess(appContext)) null else runCatching {
            val filter = appContext.getSystemService(NotificationManager::class.java)
                ?.currentInterruptionFilter
            when (filter) {
                null, NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> null
                else -> filter != NotificationManager.INTERRUPTION_FILTER_ALL
            }
        }.getOrNull()

        // 亮度與亮度模式讀取不需權限（寫回去才需要），讀不到就留 null
        val brightnessPercent = runCatching {
            val raw = Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            (raw * 100f / MAX_BRIGHTNESS).roundToInt().coerceIn(0, 100)
        }.getOrNull()
        val brightnessAuto = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrNull()

        val snapshot = Snapshot(
            volumes = volumes,
            ringerMode = ringerMode,
            dndOn = dndOn,
            brightnessPercent = brightnessPercent,
            brightnessAuto = brightnessAuto
        )
        runCatching {
            prefs(appContext).edit()
                .putString(KEY, json.encodeToString(Snapshot.serializer(), snapshot))
                .apply()
        }

        val skipped = buildList {
            if (dndOn == null) add("勿擾（無存取權）")
            if (brightnessPercent == null) add("螢幕亮度")
        }
        return if (skipped.isEmpty()) null else "未擷取：${skipped.joinToString("、")}"
    }

    /** 讀回快照；沒有（沒記過）時回 null。 */
    fun load(context: Context): Snapshot? = runCatching {
        val raw = prefs(context.applicationContext).getString(KEY, null) ?: return null
        json.decodeFromString(Snapshot.serializer(), raw)
    }.getOrNull()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** VolumeStream 對應到 AudioManager 的串流常數（與 RoutineExecutor 一致） */
    private fun audioStream(stream: VolumeStream): Int = when (stream) {
        VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
        VolumeStream.RING -> AudioManager.STREAM_RING
        VolumeStream.ALARM -> AudioManager.STREAM_ALARM
        VolumeStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
    }

    private fun hasDndAccess(context: Context): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
    }.getOrDefault(false)

    /** Android 的 SCREEN_BRIGHTNESS 值域（與 RoutineExecutor.doBrightness 一致） */
    private const val MAX_BRIGHTNESS = 255
}
