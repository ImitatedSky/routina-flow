package com.routina.app.engine

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Routine
import com.routina.app.model.Trigger
import com.routina.app.model.geoCircle
import com.routina.app.model.isLocation
import com.routina.app.model.opposite

/**
 * 區域觸發的地理圍欄註冊管理。
 *
 * 監測交給系統（Play Services Geofencing）：低耗電、不需要自建常駐定位輪詢。
 * 地理圍欄在重開機後會被系統清除，因此凡是排程可能改變的時機
 * （save / setEnabled / delete / 開機 / App 啟動）都要重新 [syncAll]。
 *
 * 所有 Play Services 呼叫都包在 try/catch 內：裝置沒有 GMS、
 * 或權限在呼叫瞬間被撤銷時，只能安靜失效，絕不讓 App 崩潰。
 */
object GeofenceManager {

    /** 全部地理圍欄共用同一個 PendingIntent（系統以 extras 帶回觸發的 id） */
    private const val REQUEST_CODE = 7301

    /** 裝置是否支援 Google Play Services（無 GMS 時區域觸發不可用） */
    fun isPlayServicesAvailable(context: Context): Boolean = runCatching {
        GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context.applicationContext) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /** 前景精確位置權限（地圖「使用目前位置」與地理圍欄的前提） */
    fun hasForegroundLocation(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * 背景位置權限（Android 10+ 的「一律允許」）。
     * Android 9 以下沒有這個權限，前景位置即等同背景可用。
     */
    fun hasBackgroundLocation(context: Context): Boolean = when {
        !hasForegroundLocation(context) -> false
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> true
        else -> hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    /**
     * 背景位置能否用 runtime 對話框請求。
     * Android 11 起系統只接受從 App 設定頁選「一律允許」。
     */
    fun canRequestBackgroundLocation(): Boolean =
        Build.VERSION.SDK_INT == Build.VERSION_CODES.Q

    /** 地理圍欄是否可註冊（GMS 可用 + 已取得背景位置權限） */
    fun isReady(context: Context): Boolean =
        isPlayServicesAvailable(context) && hasBackgroundLocation(context)

    /** 是否存在已啟用且已選好地點的區域觸發例行程序 */
    fun hasActiveLocationRoutines(context: Context): Boolean =
        RoutineRepository.get(context).routines.value.any { it.isRegisterable }

    /**
     * 依目前 routine 狀態重新對齊地理圍欄。
     *
     * 以明確的 id 清單增刪（不用 PendingIntent 整批移除），
     * 移除與新增才不會互相競速把剛註冊的圍欄清掉。
     */
    fun syncAll(context: Context) {
        val appContext = context.applicationContext
        val client = geofencingClient(appContext) ?: return
        val routines = RoutineRepository.get(appContext).routines.value

        // 未就緒（無 GMS / 缺背景位置）→ 全部移除，等授權完成後再由 ON_RESUME 重新同步
        val desired = if (isReady(appContext)) routines.mapNotNull { it.toGeofence() } else emptyList()
        val desiredIds = desired.mapTo(mutableSetOf()) { it.requestId }
        val stale = routines.map { it.id }.filterNot { it in desiredIds }

        if (stale.isNotEmpty()) runCatching { client.removeGeofences(stale) }
        if (desired.isEmpty()) return

        val pendingIntent = buildPendingIntent(appContext) ?: return
        try {
            val request = GeofencingRequest.Builder()
                // initialTrigger = 0：建立當下人就在區域內也不立刻執行
                .setInitialTrigger(0)
                .addGeofences(desired)
                .build()
            client.addGeofences(request, pendingIntent)
        } catch (t: Throwable) {
            // SecurityException（權限剛被撤銷）等：安靜失效，UI 的權限引導卡會接手
        }
    }

    /** 刪除單一 routine 的地理圍欄（刪除後就查不到它，必須在移除資料前後明確指定 id） */
    fun remove(context: Context, routineId: String) {
        val client = geofencingClient(context.applicationContext) ?: return
        runCatching { client.removeGeofences(listOf(routineId)) }
    }

    // ---------- 內部工具 ----------

    private fun geofencingClient(context: Context): GeofencingClient? =
        runCatching { LocationServices.getGeofencingClient(context) }.getOrNull()

    private fun buildPendingIntent(context: Context): PendingIntent? = runCatching {
        // 必須是 mutable：系統要把觸發的圍欄 id 與轉換類型填進 extras
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutability
        )
    }.getOrNull()

    private fun hasPermission(context: Context, permission: String): Boolean = runCatching {
        ContextCompat.checkSelfPermission(context.applicationContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** 已啟用、觸發類型為區域、且真的選過地點 */
    private val Routine.isRegisterable: Boolean
        get() = enabled && trigger.isLocation && trigger.geoCircle?.isConfigured == true

    /**
     * 要向系統註冊的轉換類型。
     * 開了「離開時還原」時連反向轉換一起註冊，否則系統根本不會送出條件結束的事件
     * （見 [RestoreOnExit]）；反向事件由 [GeofenceReceiver] 分流成還原、不執行動作。
     */
    private fun Routine.transitionMask(): Int? {
        val own = trigger.transitionType() ?: return null
        val reverse = if (restoreOnExit) trigger.opposite?.transitionType() else null
        return if (reverse != null) own or reverse else own
    }

    /** Geofence id = routine.id，天然唯一且可直接回查 */
    private fun Routine.toGeofence(): Geofence? {
        if (!isRegisterable) return null
        val circle = trigger.geoCircle ?: return null
        val transition = transitionMask() ?: return null
        return runCatching {
            Geofence.Builder()
                .setRequestId(id)
                .setCircularRegion(
                    circle.lat,
                    circle.lng,
                    circle.radiusM
                        .coerceIn(Trigger.MIN_RADIUS_M, Trigger.MAX_RADIUS_M)
                        .toFloat()
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(transition)
                .build()
        }.getOrNull()
    }

    /** 觸發類型對應的地理圍欄轉換；非區域觸發為 null */
    fun Trigger.transitionType(): Int? = when (this) {
        is Trigger.LocationEnter -> Geofence.GEOFENCE_TRANSITION_ENTER
        is Trigger.LocationExit -> Geofence.GEOFENCE_TRANSITION_EXIT
        else -> null
    }
}
