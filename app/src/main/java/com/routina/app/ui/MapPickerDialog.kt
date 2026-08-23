package com.routina.app.ui

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.location.LocationServices
import com.routina.app.engine.GeofenceManager
import com.routina.app.model.GeoCircle
import com.routina.app.model.Trigger
import com.routina.app.ui.theme.RoutinaColors
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon
import java.io.File
import kotlin.math.roundToInt

/** 尚未選點時的預設中心：台北車站一帶 */
private const val DEFAULT_LAT = 25.0330
private const val DEFAULT_LNG = 121.5654
private const val DEFAULT_ZOOM = 15.0

/** 半徑滑桿：100–1000m，步進 50m（100 起共 19 個位置 → 中間 17 個 step） */
private const val RADIUS_STEP_M = 50
private const val RADIUS_SLIDER_STEPS =
    (Trigger.MAX_RADIUS_M - Trigger.MIN_RADIUS_M) / RADIUS_STEP_M - 1

/**
 * 全螢幕地圖選點：移動地圖決定中心（畫面中央固定準星），滑桿調整半徑並即時預覽圓形範圍。
 *
 * 圖資用 OpenStreetMap（osmdroid），免 API key；圖磚快取放在 App 私有目錄，
 * 解除安裝即一併清除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerDialog(
    initial: GeoCircle,
    onConfirm: (GeoCircle) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    remember { initOsmdroid(context) }

    val start = remember {
        if (initial.isConfigured) {
            GeoPoint(initial.lat, initial.lng)
        } else {
            GeoPoint(DEFAULT_LAT, DEFAULT_LNG)
        }
    }
    var lat by remember { mutableStateOf(start.latitude) }
    var lng by remember { mutableStateOf(start.longitude) }
    var radius by remember {
        mutableStateOf(initial.radiusM.coerceIn(Trigger.MIN_RADIUS_M, Trigger.MAX_RADIUS_M))
    }
    var label by remember { mutableStateOf(initial.label) }
    var message by remember { mutableStateOf<String?>(null) }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    val circle = remember { Polygon() }

    fun moveToCurrentLocation() {
        message = null
        try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { location ->
                    if (location == null) {
                        message = "還取不到目前位置，請開啟定位後再試一次"
                    } else {
                        mapView?.controller?.animateTo(
                            GeoPoint(location.latitude, location.longitude)
                        )
                    }
                }
                .addOnFailureListener { message = "取得目前位置失敗" }
        } catch (t: Throwable) {
            // 權限在呼叫瞬間被撤銷 / 無 GMS
            message = "取得目前位置失敗"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            moveToCurrentLocation()
        } else {
            message = "未授權位置權限，無法使用目前位置"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("選擇區域") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "取消")
                            }
                        },
                        actions = {
                            TextButton(
                                onClick = {
                                    onConfirm(GeoCircle(lat, lng, radius, label.trim()))
                                }
                            ) {
                                Text("確定")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                MapView(ctx).apply {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(true)
                                    zoomController.setVisibility(
                                        CustomZoomButtonsController.Visibility.NEVER
                                    )
                                    controller.setZoom(DEFAULT_ZOOM)
                                    controller.setCenter(start)
                                    circle.outlinePaint.color = CIRCLE_OUTLINE
                                    circle.outlinePaint.strokeWidth = 4f
                                    circle.fillPaint.color = CIRCLE_FILL
                                    overlays.add(circle)
                                    // 地圖移動 / 縮放後把中心點同步回狀態，圓形與座標即時跟上
                                    addMapListener(object : MapListener {
                                        override fun onScroll(event: ScrollEvent?): Boolean {
                                            lat = mapCenter.latitude
                                            lng = mapCenter.longitude
                                            return true
                                        }

                                        override fun onZoom(event: ZoomEvent?): Boolean {
                                            lat = mapCenter.latitude
                                            lng = mapCenter.longitude
                                            return true
                                        }
                                    })
                                    onResume()
                                    mapView = this
                                }
                            },
                            update = { map ->
                                circle.points =
                                    Polygon.pointsAsCircle(GeoPoint(lat, lng), radius.toDouble())
                                map.invalidate()
                            },
                            onRelease = { map ->
                                mapView = null
                                map.onPause()
                                map.onDetach()
                            }
                        )

                        // 中央準星：地圖中心即為區域中心
                        Icon(
                            imageVector = Icons.Filled.Place,
                            contentDescription = "區域中心",
                            tint = LocationAccent,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(40.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(16.dp)
                    ) {
                        Text(
                            "半徑 $radius 公尺",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Slider(
                            value = radius.toFloat(),
                            onValueChange = {
                                radius = it.roundToInt()
                                    .coerceIn(Trigger.MIN_RADIUS_M, Trigger.MAX_RADIUS_M)
                            },
                            valueRange = Trigger.MIN_RADIUS_M.toFloat()..
                                Trigger.MAX_RADIUS_M.toFloat(),
                            steps = RADIUS_SLIDER_STEPS
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "%.4f, %.4f".format(lat, lng),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = {
                                    if (GeofenceManager.hasForegroundLocation(context)) {
                                        moveToCurrentLocation()
                                    } else {
                                        locationPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.MyLocation,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(6.dp))
                                Text("使用目前位置")
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text("地點名稱（選填）") },
                            placeholder = { Text("例如：公司") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        message?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 區域圓形與中央準星都沿用「進入區域」積木的顏色——色表集中在 [RoutinaColors]，
 * 這裡不再自己硬編一組青藍色（積木改色時地圖也會跟著改）。
 */
private val LocationAccent = RoutinaColors.TriggerLocationEnter
private val CIRCLE_OUTLINE = LocationAccent.copy(alpha = 220f / 255f).toArgb()
private val CIRCLE_FILL = LocationAccent.copy(alpha = 50f / 255f).toArgb()

/**
 * osmdroid 初始化：圖磚快取放在 App 私有目錄（免 storage 權限、解除安裝即清除），
 * User-Agent 用 applicationId（OSM 圖磚伺服器要求可辨識的 UA，否則會被擋）。
 */
private fun initOsmdroid(context: Context) {
    runCatching {
        val appContext = context.applicationContext
        val config = Configuration.getInstance()
        // 先 load 讀進預設值，再覆寫成我們要的路徑與 UA
        config.load(
            appContext,
            appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        val base = File(appContext.filesDir, "osmdroid")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")
        config.userAgentValue = appContext.packageName
    }
}
