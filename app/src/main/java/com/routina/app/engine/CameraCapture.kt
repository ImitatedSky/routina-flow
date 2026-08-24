package com.routina.app.engine

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 無預覽的相機擷取（CameraX ImageCapture）。
 *
 * 一般的 CameraX 用法會綁在 Activity/Fragment 的生命週期上，但這裡是在服務或 App 行程內
 * 「拍一張就結束」——沒有畫面、也沒有現成的 LifecycleOwner。作法是自建一個
 * [LifecycleRegistry] 當 owner，綁定前手動推到 RESUMED、拍完解除綁定並回到 DESTROYED，
 * 相機資源隨即釋放，平時零佔用。
 *
 * CameraX 的 provider 取得、bindToLifecycle、以及 LifecycleRegistry 的狀態變更都必須在
 * 主執行緒進行（[capture] 內以 [withContext] 切到 Main），擷取回呼則在主執行緒的 executor 上。
 *
 * 相機為背景可存取與否由呼叫端（RoutineExecutor / ExecutionService）以前景服務類型把關；
 * 這裡只負責「能存取時」把相片存進相簿。無相機硬體、鏡頭開啟失敗都會丟例外，由呼叫端記為失敗。
 */
object CameraCapture {

    private const val RELATIVE_DIR = "Routina"

    /** FileProvider 的 authority 後綴（Manifest 宣告為 "${applicationId}.fileprovider"） */
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /**
     * 一次擷取的結果：可跨 App 檢視的 content URI 清單（供通知點擊開啟）與最後一張的檔名
     * （通知內文用）。連拍時通知只用最後一張的 URI。
     */
    data class Result(val uris: List<Uri>, val displayName: String)

    /** 單張擷取的中間結果 */
    private data class Shot(val uri: Uri, val displayName: String)

    /**
     * 以 [lensBack] 選定的鏡頭連續擷取 [count] 張，每張間隔 [intervalMs] 毫秒
     * （單張拍照即 count=1、intervalMs 忽略）。全程共用同一次相機綁定。
     *
     * @return 已存入的相片結果（content URI 清單 + 最後一張檔名）
     */
    suspend fun capture(
        context: Context,
        lensBack: Boolean,
        count: Int,
        intervalMs: Long,
        shareToGallery: Boolean = false
    ): Result {
        val appContext = context.applicationContext
        val selector = if (lensBack) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }

        val provider = awaitProvider(appContext)
        val available = runCatching { provider.hasCamera(selector) }.getOrDefault(false)
        if (!available) {
            throw IllegalStateException(if (lensBack) "沒有可用的後鏡頭" else "沒有可用的前鏡頭")
        }

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        val owner = CaptureLifecycleOwner()

        withContext(Dispatchers.Main) {
            owner.start()
            provider.unbindAll()
            provider.bindToLifecycle(owner, selector, imageCapture)
        }
        try {
            val shots = ArrayList<Shot>(count)
            repeat(count) { index ->
                shots += takeOne(appContext, imageCapture, shareToGallery)
                if (index < count - 1 && intervalMs > 0) delay(intervalMs)
            }
            return Result(shots.map { it.uri }, shots.lastOrNull()?.displayName.orEmpty())
        } finally {
            withContext(Dispatchers.Main) {
                provider.unbindAll()
                owner.stop()
            }
        }
    }

    /** 取得 ProcessCameraProvider（ListenableFuture → suspend，免額外的 guava-coroutines 依賴） */
    private suspend fun awaitProvider(context: Context): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (t: Throwable) {
                        cont.resumeWithException(t)
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }

    /**
     * 擷取一張並存檔；成功回傳可跨 App 檢視的 content URI 與檔名，失敗（含逾時）丟例外。
     *
     * 預設 [shareToGallery] = false → 存進 **App 私有外部目錄**（其他 App 讀不到、不進相簿／雲端備份），
     * 以 FileProvider 產生 content URI 供通知點擊檢視。
     * [shareToGallery] = true 才存進公開相簿：API 29+ 用 MediaStore、API ≤28 用公開目錄。
     */
    private suspend fun takeOne(
        context: Context,
        imageCapture: ImageCapture,
        shareToGallery: Boolean
    ): Shot =
        suspendCancellableCoroutine { cont ->
            val name = "Routina_" + timestamp()
            val displayName = "$name.jpg"
            // 只有「存到公開相簿」且 API 29+ 才走 MediaStore；其餘（私有、或公開的 ≤28）走檔案 + FileProvider
            val useMediaStore = shareToGallery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            var targetFile: File? = null
            val options = if (useMediaStore) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/" + RELATIVE_DIR
                    )
                }
                ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ).build()
            } else {
                val dir = if (shareToGallery) {
                    // API ≤28 存公開相簿目錄（需 WRITE_EXTERNAL_STORAGE，maxSdk 28）
                    @Suppress("DEPRECATION")
                    File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                        RELATIVE_DIR
                    )
                } else {
                    // 預設：App 私有外部目錄，免權限、其他 App 讀不到
                    File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), RELATIVE_DIR)
                }.apply { mkdirs() }
                val file = File(dir, displayName)
                targetFile = file
                ImageCapture.OutputFileOptions.Builder(file).build()
            }

            imageCapture.takePicture(
                options,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val uri = if (useMediaStore) {
                            output.savedUri ?: Uri.EMPTY
                        } else {
                            // file:// 無法給系統檢視器讀取，改用 FileProvider content URI
                            targetFile?.let { fileProviderUri(context, it) }
                                ?: output.savedUri ?: Uri.EMPTY
                        }
                        cont.resume(Shot(uri, displayName))
                    }

                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                }
            )
        }

    /** 以 FileProvider 為公開目錄的檔案產生可授權讀取的 content URI（API ≤28 路徑用） */
    private fun fileProviderUri(context: Context, file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)
    }.getOrNull()

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())

    /**
     * 服務／App 行程內臨時使用的 LifecycleOwner：綁定相機期間維持 RESUMED，
     * 擷取完成回到 DESTROYED 讓 CameraX 釋放資源。狀態變更皆須在主執行緒呼叫。
     */
    private class CaptureLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun start() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
