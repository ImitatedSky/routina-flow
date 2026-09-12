package com.routina.app.data

import android.content.Context
import android.net.Uri
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
 */
@Serializable
data class RoutineBackup(
    val format: Int = FORMAT_VERSION,
    /** 匯出當下的 App 版本，只用來診斷，不影響讀取 */
    val appVersion: String = "",
    val exportedAt: Long = System.currentTimeMillis(),
    val routines: List<Routine> = emptyList()
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

/**
 * 備份檔的讀寫。檔案位置交給系統的檔案選擇器（SAF）決定，
 * App 不碰任何共用儲存空間、也不需要儲存權限。
 */
object RoutineBackupIo {

    // ignoreUnknownKeys：讓新版匯出的檔在舊版 App 也能讀（多出來的欄位略過），
    // 與 RoutineRepository 對 routines.json 的寬容度一致。
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /** 匯出時預設的檔名，帶日期方便分辨哪一份是哪天的 */
    fun suggestedFileName(): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return "routina-backup-$day.json"
    }

    /** 寫入備份檔，成功時回傳寫了幾支程序 */
    fun write(context: Context, uri: Uri, routines: List<Routine>): Result<Int> = runCatching {
        val backup = RoutineBackup(
            appVersion = appVersion(context),
            routines = routines
        )
        val text = json.encodeToString(RoutineBackup.serializer(), backup)
        openTruncating(context, uri).use { out ->
            out.write(text.toByteArray())
        }
        routines.size
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
        if (backup.routines.isEmpty()) {
            error("這份備份裡沒有任何例行程序")
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
