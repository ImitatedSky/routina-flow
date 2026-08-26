package com.routina.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.routina.app.engine.NfcTagReader

/**
 * 把 NFC reader mode 綁在 Activity 的 resumed 狀態上（系統要求），並回報 NFC 開關狀態。
 *
 * 去 NFC 設定頁再回來要重新啟用，離開畫面就解除；觀察者加入時會補送目前狀態的事件，
 * 因此對話框一開就會啟用一次。
 *
 * 同一個 Activity 同時只能有一種 reader mode（後啟用的會取代前一個），
 * 所以掃描與寫入對話框以 [active] 互相讓位——關掉的那一邊會先解除，
 * 接手的那一邊才啟用。
 *
 * @param enable 實際要啟用的模式（掃描或寫入）
 */
@Composable
internal fun NfcReaderModeEffect(
    activity: Activity?,
    active: Boolean,
    onNfcEnabledChange: (Boolean) -> Unit,
    enable: (Activity) -> Unit
) {
    val context = LocalContext.current
    val currentEnable by rememberUpdatedState(enable)
    val currentOnChange by rememberUpdatedState(onNfcEnabledChange)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity, active) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val nfcOn = NfcTagReader.isEnabled(context)
                    currentOnChange(nfcOn)
                    if (activity != null && active && nfcOn) currentEnable(activity)
                }

                Lifecycle.Event.ON_PAUSE ->
                    activity?.let { NfcTagReader.disableReaderMode(it) }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.let { NfcTagReader.disableReaderMode(it) }
        }
    }
}

/** 從 Compose 的 Context 找出宿主 Activity（NFC reader mode 必須綁定 Activity） */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
