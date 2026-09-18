package com.routina.app.data

import android.content.Context
import android.net.Uri
import com.routina.app.model.GlobalVar
import com.routina.app.model.NfcRecord
import com.routina.app.model.Routine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 備份檔的內容。
 *
 * 外面包一層信封（而不是直接存 List<Routine>）是為了帶 [format]：
 * 日後格式若有變動，讀檔時才分得出「舊版可讀」與「新版讀不懂」，
 * 不會把不相容的檔硬解成半殘的資料。
 *
 * **格式 2 起連 NFC 標籤、全域變數與偏好一起備份。** 格式 1 只有 [routines]，
 * 換手機或誤刪 App 時那些東西就沒了——而它們同樣是使用者累積出來、重建不回來的資料。
 * 讀舊檔時新欄位會是空的，照樣讀得進來。
 */
@Serializable
data class RoutineBackup(
    val format: Int = FORMAT_VERSION,
    /** 匯出當下的 App 版本，只用來診斷，不影響讀取 */
    val appVersion: String = "",
    val exportedAt: Long = System.currentTimeMillis(),
    val routines: List<Routine> = emptyList(),
    val nfcTags: List<NfcRecord> = emptyList(),
    val globals: List<GlobalVar> = emptyList(),
    val settings: BackupSettings? = null
) {
    /** 整份備份是不是空的。只有例行程序為空不算空——可能是只備份了標籤或變數 */
    val isEmpty: Boolean
        get() = routines.isEmpty() && nfcTags.isEmpty() && globals.isEmpty() && settings == null

    /** 例行程序以外還帶了什麼，給匯入畫面說明用 */
    val hasExtras: Boolean
        get() = nfcTags.isNotEmpty() || globals.isNotEmpty() || settings != null

    companion object {
        const val FORMAT_VERSION = 2
    }
}

/** 偏好設定的快照。只收使用者真的調過、且換裝置後會想留著的三項 */
@Serializable
data class BackupSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val runToast: Boolean = true,
    val logLimit: Int = AppSettings.DEFAULT_LOG_LIMIT
)

/**
 * 備份檔的讀寫。檔案位置交給系統的檔案選擇器（SAF），
 * App 不碰任何共用儲存空間、也不需要儲存權限。
 */
object RoutineBackupIo {

    // ignoreUnknownKeys：讓新版匯出的檔在舊版 App 也能讀（多出來的欄位略過），
    // 與 RoutineRepository 對 routines.json 的寬容度一致。
    // internal 而非 private：單元測試要驗的正是這一份設定的寬容度
    internal val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** 匯出時預設的檔名，帶日期方便分辨哪一份是哪天的 */
    fun suggestedFileName(): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return "routina-backup-$day.json"
    }

    /** 寫入備份檔，成功時回傳寫進去的內容（呼叫端用它組回饋訊息） */
    fun write(
        context: Context,
        uri: Uri,
        routines: List<Routine>,
        nfcTags: List<NfcRecord> = emptyList(),
        globals: List<GlobalVar> = emptyList(),
        settings: BackupSettings? = null
    ): Result<RoutineBackup> = runCatching {
        val backup = RoutineBackup(
            appVersion = appVersion(context),
            routines = routines,
            nfcTags = nfcTags,
            globals = globals,
            settings = settings
        )
        val text = json.encodeToString(RoutineBackup.serializer(), backup)
        openTruncating(context, uri).use { out ->
            out.write(text.toByteArray())
        }
        backup
    }.recoverCatching { e ->
        throw IllegalStateException(e.message ?: "匯出失敗", e)
    }

    /** 讀取備份檔並驗證內容，失敗時附上看得懂的原因 */
    fun read(context: Context, uri: Uri): Result<RoutineBackup> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().decodeToString()
        } ?: error("無法讀取所選檔案")

        val backup = try {
            json.decodeFromString(RoutineBackup.serializer(), text)
        } catch (e: Exception) {
            throw IllegalStateException("這個檔案不是 Routina 的備份，或內容已損壞", e)
        }

        if (backup.format > RoutineBackup.FORMAT_VERSION) {
            error("這份備份是較新版本的 Routina 匯出的（格式 v${backup.format}），請先更新 App 再匯入")
        }
        if (backup.isEmpty) {
            error("這份備份裡沒有任何內容")
        }
        backup
    }

    /**
     * 開啟輸出串流。優先用 "wt"（t＝truncate）：覆寫既有檔案時要先清空，
     * 否則舊內容較長時會殘留尾巴、變成壞掉的 JSON。
     * 但 truncate 不是每個檔案提供者都支援，不支援時退回 "w"（新建檔案本來就是空的，沒有差別）。
     */
    private fun openTruncating(context: Context, uri: Uri): OutputStream =
        try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (e: IllegalArgumentException) {
            context.contentResolver.openOutputStream(uri, "w")
        } catch (e: UnsupportedOperationException) {
            context.contentResolver.openOutputStream(uri, "w")
        } ?: error("無法寫入所選位置")

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}
