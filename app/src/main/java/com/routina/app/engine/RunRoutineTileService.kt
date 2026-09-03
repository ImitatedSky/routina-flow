package com.routina.app.engine

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.routina.app.R

/**
 * 快速設定磚：一鍵執行使用者在 App 內指定的例行程序。
 *
 * 磚的標籤顯示指定程序的名稱（未指定或已刪除時退回「Routina」）。點擊時直接在進程層級
 * scope 執行（見 [RunFromOutside]）——TileService 要啟動 Activity 得用 startActivityAndCollapse，
 * 且 Android 14 起強制改用 PendingIntent 版；直接執行可完全避開這段版本差異，也少一次跳板。
 * 未指定程序時只跳一則 Toast 提示去 App 內設定，不崩潰。
 */
class RunRoutineTileService : TileService() {

    /** 面板展開（開始監聽）時更新標籤，讓磚顯示目前指定的程序名稱 */
    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = RunFromOutside.tileRoutineName(this) ?: getString(R.string.app_name)
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        RunFromOutside.run(this, RunFromOutside.tileRoutineId(this))
    }
}
