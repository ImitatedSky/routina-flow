package com.routina.app.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 定時長度的音訊錄製（MediaRecorder，MPEG-4 / AAC）。
 *
 * 於呼叫端的 coroutine 內 `start` → `delay(seconds)` → `stop/release`。存檔位置：
 * - API 29+：`MediaStore` 的 `Music/Routina`（免權限，IS_PENDING 收尾）
 * - API 28 以下：App 專屬外部目錄（`getExternalFilesDir`，同樣免權限）
 *
 * 麥克風為背景可存取與否由呼叫端以前景服務類型把關；這裡只在「能存取時」錄音。
 * 太短或無音源時 `stop` 會丟例外（MediaRecorder 未收到任何資料），一律 catch 後仍釋放資源，
 * 存檔位置回傳給呼叫端寫入紀錄。
 */
object AudioRecorder {

    private const val RELATIVE_DIR = "Routina"

    /** FileProvider 的 authority 後綴（Manifest 宣告為 "${applicationId}.fileprovider"） */
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /**
     * 錄音結果：實際存檔位置（顯示用）與可跨 App 檢視的 content URI（供通知點擊開啟）。
     * API 29+ 為 MediaStore URI；API ≤28 為 App 專屬外部目錄檔案的 FileProvider URI。
     */
    data class Result(val displayPath: String, val uri: Uri?)

    /** 錄製 [seconds] 秒音訊並存檔，回傳存檔位置說明。錄製或存檔失敗時丟例外。 */
    suspend fun record(context: Context, seconds: Int): Result {
        val appContext = context.applicationContext
        val name = "Routina_" + timestamp()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            recordToMediaStore(appContext, name, seconds)
        } else {
            recordToAppDir(appContext, name, seconds)
        }
    }

    private suspend fun recordToMediaStore(context: Context, name: String, seconds: Int): Result {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.m4a")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_MUSIC + "/" + RELATIVE_DIR
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("無法建立錄音檔")

        try {
            resolver.openFileDescriptor(uri, "w").use { pfd ->
                val descriptor = pfd?.fileDescriptor
                    ?: throw IllegalStateException("無法開啟錄音檔")
                recordFor(context, seconds) { setOutputFile(descriptor) }
            }
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }

        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return Result("音樂/$RELATIVE_DIR/$name.m4a", uri)
    }

    private suspend fun recordToAppDir(context: Context, name: String, seconds: Int): Result {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), RELATIVE_DIR)
            .apply { mkdirs() }
        val file = File(dir, "$name.m4a")
        try {
            recordFor(context, seconds) { setOutputFile(file.absolutePath) }
        } catch (t: Throwable) {
            runCatching { file.delete() }
            throw t
        }
        // file:// 無法給系統播放器讀取，改用 FileProvider content URI 供通知點擊開啟
        val uri = runCatching {
            FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)
        }.getOrNull()
        return Result(file.absolutePath, uri)
    }

    // ---------- 錄音機生命週期 ----------

    /** 建立 MediaRecorder、錄 [seconds] 秒後停止並釋放。準備／啟動失敗會丟例外並先釋放。 */
    private suspend fun recordFor(
        context: Context,
        seconds: Int,
        setOutput: MediaRecorder.() -> Unit
    ) {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutput()
                prepare()
                start()
            }
        } catch (t: Throwable) {
            runCatching { recorder.release() }
            throw t
        }
        try {
            delay(seconds * 1000L)
        } finally {
            // 太短或無音源時 stop 會丟 RuntimeException：吞掉但仍要 release 釋放麥克風
            runCatching { recorder.stop() }
            runCatching { recorder.release() }
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
}
