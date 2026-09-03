package com.routina.app.engine

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.Location
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.AlarmClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.routina.app.R
import com.routina.app.RoutinaApp
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Action
import com.routina.app.model.ActionResult
import com.routina.app.model.CompareOp
import com.routina.app.model.Condition
import com.routina.app.model.LocationFormat
import com.routina.app.model.MathOp
import com.routina.app.model.RingerModeType
import com.routina.app.model.Routine
import com.routina.app.model.RunLog
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource
import com.routina.app.model.VolumeStream
import com.routina.app.model.usesRightOperand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * 依序執行例行程序的所有動作。
 *
 * 規則：動作依加入順序執行，單一動作失敗不中斷其餘動作，
 * 最後把每個動作的成敗寫入 RunLog。
 *
 * 執行本身是 suspend 函式：等待、朗讀、HTTP 等長時動作需要協程才能在不阻塞
 * 主執行緒的前提下完成（背景觸發時由 [ExecutionService] 承載）。
 */
object RoutineExecutor {

    /**
     * 前景服務類型切換：讓引擎在拍照／錄音前，把宿主前景服務切到 camera／microphone 類型
     * （Android 9+ 存取相機麥克風、Android 14+ 背景啟動該類前景服務的前提）。
     *
     * 手動執行（App 在前景、無執行服務）時傳 null：直接嘗試即可。背景觸發由 [ExecutionService]
     * 提供實作；[switchTo] 回傳 false 代表系統擋下了背景相機／麥克風，動作應記為受限失敗。
     */
    interface ForegroundTypeSwitch {
        /** @return 是否已成功以指定類型進入前景 */
        fun switchTo(type: CaptureFgsType): Boolean

        /** 動作完成後還原回預設（specialUse）類型 */
        fun restore()
    }

    /** 需要專屬前景服務類型的擷取動作硬體 */
    enum class CaptureFgsType { CAMERA, MICROPHONE }

    /**
     * @param persistBlocking 背景元件（Receiver）觸發時為 true：
     * 行程可能在返回後立即被回收，必須確保紀錄已寫入磁碟。
     * 由 UI 手動執行或長駐服務觸發時為 false，避免不必要的同步 I/O。
     * @param allowWait 是否執行「等待」動作。前景執行服務啟動失敗而降級為
     * 接收器內同步執行時為 false（廣播接收器有時限，不能等）。
     * @param note 執行環境的補充說明，會寫進 RunLog（例如降級執行）。
     * @param fgsSwitch 拍照／錄音動作用的前景服務類型切換；手動執行為 null。
     * @param triggerContext 觸發帶來的情境值（如通知標題/內容）；手動與定時執行為空。
     * @return 這次執行的紀錄（呼叫端可據此顯示結果）
     */
    suspend fun execute(
        context: Context,
        routine: Routine,
        source: TriggerSource,
        persistBlocking: Boolean = false,
        allowWait: Boolean = true,
        note: String? = null,
        fgsSwitch: ForegroundTypeSwitch? = null,
        triggerContext: Map<String, String> = emptyMap()
    ): RunLog {
        val appContext = context.applicationContext
        val repository = RoutineRepository.get(appContext)

        // 觸發生命週期：非手動觸發才受「觸發上限次數 / 結束日期」限制。
        // 已達期限（排程可能還沒被拆掉又觸發了）→ 自動停用並跳過這次執行，不跑動作。
        if (source != TriggerSource.MANUAL) {
            val now = System.currentTimeMillis()
            val expired = routine.expiresAt?.let { now >= it } == true
            val maxed = routine.maxRuns?.let { routine.runCount >= it } == true
            if (expired || maxed) {
                RoutineManager.setEnabled(appContext, routine.id, false)
                val why = if (expired) "已到結束日期" else "已達觸發次數上限"
                val skipLog = RunLog(
                    routineId = routine.id,
                    routineName = routine.name,
                    source = source,
                    results = emptyList(),
                    note = "$why，未執行並已自動停用"
                )
                if (persistBlocking) repository.addLogBlocking(skipLog) else repository.addLog(skipLog)
                return skipLog
            }
        }

        // Android 10+ 起，從 Receiver / Service 呼叫 startActivity 會被系統靜默丟棄
        // （不丟例外）。只有下列情境能真正把 Activity 帶到前景：
        // 手動執行、由 Activity context 呼叫、或已取得「顯示在其他應用程式上層」權限。
        val canLaunchActivity = source == TriggerSource.MANUAL ||
            context is Activity ||
            canDrawOverlays(appContext)

        // 一次執行建立一個情境，貫穿所有動作。觸發情境依序疊加：
        // 常用（時間/日期/星期/電量）→ 由 routine 觸發設定推得（地點/裝置名稱等）→
        // 呼叫端傳入的當次觸發實際值（如實際通知內容，可覆寫前面的預設）。
        val ctx = RunContext().apply {
            trigger.putAll(commonTriggerValues(appContext))
            trigger.putAll(triggerContextFromRoutine(routine.trigger))
            trigger.putAll(triggerContext)
            // 全域變數在執行開始時載入，`{{全域:名稱}}` 讀得到；期間的寫入結束時才落地
            globals.putAll(repository.globalsSnapshot())
            // 最外層程序先入呼叫堆疊，讓「執行程序」動作能擋住繞回自己的循環呼叫
            callStack.add(routine.id)
        }

        // 動作以直譯器執行（支援 如果／否則／while／重複 的流程控制）；扁平清單用配對標記表達層級。
        val results = mutableListOf<ActionResult>()
        val budget = intArrayOf(Action.MAX_ACTIONS_PER_RUN)
        runProgram(
            routine.actions, 0, routine.actions.size,
            appContext, routine, source, canLaunchActivity, allowWait, fgsSwitch, ctx,
            results, budget
        )

        // 這次執行若有寫入全域變數，只把有異動的鍵合併落地（背景執行同步寫入以防行程結束）
        if (ctx.dirtyGlobals.isNotEmpty()) {
            repository.applyGlobals(ctx.globals.filterKeys { it in ctx.dirtyGlobals }, persistBlocking)
        }

        // 觸發執行完成 → 記一次數；達上限或已過期就自動停用（setEnabled 連帶取消排程/監測）
        if (source != TriggerSource.MANUAL) {
            repository.incrementRunCount(routine.id, persistBlocking)
            val newCount = routine.runCount + 1
            val reachedMax = routine.maxRuns?.let { newCount >= it } == true
            val nowExpired = routine.expiresAt?.let { System.currentTimeMillis() >= it } == true
            if (reachedMax || nowExpired) {
                RoutineManager.setEnabled(appContext, routine.id, false)
            }
        }

        val log = RunLog(
            routineId = routine.id,
            routineName = routine.name,
            source = source,
            results = results,
            note = note
        )
        if (persistBlocking) repository.addLogBlocking(log) else repository.addLog(log)
        return log
    }

    /**
     * 流程控制直譯器：以游標走 [actions] 的 [from, to) 區間，遇到配對標記就分支／回跳。
     * 巢狀以遞迴處理；對不成對的標記保持穩健（未關閉的區塊延伸到範圍尾），並以 [budget] 擋失控。
     */
    private suspend fun runProgram(
        actions: List<Action>,
        from: Int,
        to: Int,
        context: Context,
        routine: Routine,
        source: TriggerSource,
        canLaunchActivity: Boolean,
        allowWait: Boolean,
        fgsSwitch: ForegroundTypeSwitch?,
        ctx: RunContext,
        results: MutableList<ActionResult>,
        budget: IntArray
    ) {
        var i = from
        while (i < to) {
            if (budget[0] <= 0) {
                results.add(ActionResult("已達單次執行的動作上限，流程中止", false, "action budget exceeded"))
                return
            }
            when (val action = actions[i]) {
                is Action.IfBegin -> {
                    val end = matchingEnd(actions, i, to)
                    runIfBranches(
                        actions, i, end, context, routine, source, canLaunchActivity,
                        allowWait, fgsSwitch, ctx, results, budget
                    )
                    i = end + 1
                }

                is Action.WhileBegin -> {
                    val end = matchingEnd(actions, i, to)
                    val saved = ctx.trigger[LOOP_INDEX_KEY]
                    var guard = 0
                    while (guard < Action.WHILE_MAX_ITERATIONS && budget[0] > 0 &&
                        ConditionEvaluator.eval(action.condition, ctx)
                    ) {
                        ctx.trigger[LOOP_INDEX_KEY] = (guard + 1).toString()
                        runProgram(
                            actions, i + 1, end, context, routine, source, canLaunchActivity,
                            allowWait, fgsSwitch, ctx, results, budget
                        )
                        guard++
                    }
                    if (guard >= Action.WHILE_MAX_ITERATIONS) {
                        results.add(
                            ActionResult("while 迴圈達 ${Action.WHILE_MAX_ITERATIONS} 次上限，已中止", false, "while limit")
                        )
                    }
                    restoreLoopIndex(ctx, saved)
                    i = end + 1
                }

                is Action.RepeatBegin -> {
                    val end = matchingEnd(actions, i, to)
                    val count = resolveRepeatCount(action, ctx)
                    val saved = ctx.trigger[LOOP_INDEX_KEY]
                    var k = 0
                    while (k < count && budget[0] > 0) {
                        ctx.trigger[LOOP_INDEX_KEY] = (k + 1).toString()
                        runProgram(
                            actions, i + 1, end, context, routine, source, canLaunchActivity,
                            allowWait, fgsSwitch, ctx, results, budget
                        )
                        k++
                    }
                    restoreLoopIndex(ctx, saved)
                    i = end + 1
                }

                is Action.RunRoutine -> {
                    runSubRoutine(
                        action, context, source, canLaunchActivity, allowWait,
                        fgsSwitch, ctx, results, budget
                    )
                    i++
                }

                // 直接走到的孤立標記（正常流程下配對跳轉不會停在這）→ 略過
                is Action.ElseIf, is Action.Else, is Action.EndIf,
                is Action.EndWhile, is Action.EndRepeat -> i++

                else -> {
                    budget[0]--
                    results.add(
                        runAction(
                            context, routine, i, action, source, canLaunchActivity,
                            allowWait, fgsSwitch, ctx
                        )
                    )
                    i++
                }
            }
        }
    }

    /** 執行一個 如果 區塊：依序評估 IfBegin／各 ElseIf／Else，只執行第一個成立分支的 body */
    private suspend fun runIfBranches(
        actions: List<Action>,
        ifStart: Int,
        ifEnd: Int,
        context: Context,
        routine: Routine,
        source: TriggerSource,
        canLaunchActivity: Boolean,
        allowWait: Boolean,
        fgsSwitch: ForegroundTypeSwitch?,
        ctx: RunContext,
        results: MutableList<ActionResult>,
        budget: IntArray
    ) {
        var marker = ifStart
        while (marker < ifEnd) {
            val take = when (val m = actions[marker]) {
                is Action.IfBegin -> ConditionEvaluator.eval(m.condition, ctx)
                is Action.ElseIf -> ConditionEvaluator.eval(m.condition, ctx)
                is Action.Else -> true
                else -> false
            }
            val next = nextBranchOrEnd(actions, marker, ifEnd)
            if (take) {
                runProgram(
                    actions, marker + 1, next, context, routine, source, canLaunchActivity,
                    allowWait, fgsSwitch, ctx, results, budget
                )
                return
            }
            marker = next
        }
    }

    /** 從 [start] 起找同層的配對結束標記（任一種 End）；找不到（未關閉）回傳 [to] */
    private fun matchingEnd(actions: List<Action>, start: Int, to: Int): Int {
        var depth = 0
        var j = start + 1
        while (j < to) {
            when (actions[j]) {
                is Action.IfBegin, is Action.WhileBegin, is Action.RepeatBegin -> depth++
                is Action.EndIf, is Action.EndWhile, is Action.EndRepeat ->
                    if (depth == 0) return j else depth--

                else -> {}
            }
            j++
        }
        return to
    }

    /** 從 [from] 起找同層的下一個分支標記（ElseIf／Else）或結束標記；找不到回傳 [to] */
    private fun nextBranchOrEnd(actions: List<Action>, from: Int, to: Int): Int {
        var depth = 0
        var j = from + 1
        while (j < to) {
            when (actions[j]) {
                is Action.IfBegin, is Action.WhileBegin, is Action.RepeatBegin -> depth++
                is Action.EndIf, is Action.EndWhile, is Action.EndRepeat ->
                    if (depth == 0) return j else depth--

                is Action.ElseIf, is Action.Else -> if (depth == 0) return j
                else -> {}
            }
            j++
        }
        return to
    }

    /** 重複 N 次的次數：countExpr 非空先變數解析，夾在安全範圍內 */
    private fun resolveRepeatCount(action: Action.RepeatBegin, ctx: RunContext): Int {
        val n = if (action.countExpr.isBlank()) action.count
        else VariableResolver.resolve(action.countExpr, ctx).trim().toIntOrNull() ?: action.count
        return n.coerceIn(Action.REPEAT_COUNT_SAFE)
    }

    /** 迴圈結束後還原 {{迴圈:次數}}（支援巢狀：還原成外層的值，最外層則移除） */
    private fun restoreLoopIndex(ctx: RunContext, saved: String?) {
        if (saved != null) ctx.trigger[LOOP_INDEX_KEY] = saved else ctx.trigger.remove(LOOP_INDEX_KEY)
    }

    /** 迴圈計數在情境中的鍵；以 {{迴圈:次數}} 引用 */
    private const val LOOP_INDEX_KEY = "迴圈:次數"

    /** 「執行程序」的最大巢狀深度，配合呼叫堆疊擋住循環／過深呼叫 */
    private const val MAX_CALL_DEPTH = 10

    /**
     * 「執行程序」動作：把目標程序的動作用同一套直譯器、同一個情境與預算跑一遍
     * （變數與 {{result}} 因此串接流動）。目標不存在、循環呼叫或巢狀過深時記一筆並略過，不執行。
     */
    private suspend fun runSubRoutine(
        action: Action.RunRoutine,
        context: Context,
        source: TriggerSource,
        canLaunchActivity: Boolean,
        allowWait: Boolean,
        fgsSwitch: ForegroundTypeSwitch?,
        ctx: RunContext,
        results: MutableList<ActionResult>,
        budget: IntArray
    ) {
        val label = "執行程序：${action.routineName.ifBlank { "(未選)" }}"
        val target = action.routineId.takeIf { it.isNotBlank() }
            ?.let { RoutineRepository.get(context).findById(it) }
        if (target == null) {
            results.add(ActionResult(label, false, "找不到程序（可能已被刪除）"))
            return
        }
        if (target.id in ctx.callStack || ctx.callStack.size >= MAX_CALL_DEPTH) {
            results.add(ActionResult("執行程序：${target.name}", false, "略過：避免循環呼叫或巢狀過深"))
            return
        }
        results.add(ActionResult("執行程序：${target.name}", true))
        ctx.callStack.add(target.id)
        try {
            runProgram(
                target.actions, 0, target.actions.size, context, target, source,
                canLaunchActivity, allowWait, fgsSwitch, ctx, results, budget
            )
        } finally {
            ctx.callStack.remove(target.id)
        }
    }

    /**
     * 執行單一動作。動作函式回傳非 null 字串時視為補充說明，
     * 會附加在紀錄的描述後面（例如背景無法直接啟動而改以通知呈現）。
     */
    private suspend fun runAction(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action,
        source: TriggerSource,
        canLaunchActivity: Boolean,
        allowWait: Boolean,
        fgsSwitch: ForegroundTypeSwitch?,
        ctx: RunContext
    ): ActionResult {
        // 先把文字參數的變數 token 代入實際值；描述與遮罩都以代入後的內容為準
        val resolved = resolveAction(action, ctx)
        val description = describe(context, resolved)
        return try {
            val note = when (resolved) {
                is Action.Notify -> doNotify(context, routine, index, resolved)
                is Action.OpenApp -> doOpenApp(context, routine, index, resolved, canLaunchActivity)
                is Action.OpenUrl -> doOpenUrl(context, routine, index, resolved, canLaunchActivity)
                is Action.Share -> doShare(context, routine, index, resolved, canLaunchActivity)
                is Action.MediaVolume -> doMediaVolume(context, resolved, ctx)
                is Action.RingerMode -> doRingerMode(context, resolved)
                is Action.Bluetooth -> doBluetooth(context, routine, index, resolved)
                is Action.WifiToggle -> doWifiToggle(context, routine, index, resolved, canLaunchActivity)
                is Action.Flashlight -> doFlashlight(context, resolved)
                is Action.Speak -> doSpeak(context, resolved)
                is Action.Vibrate -> doVibrate(context, resolved, ctx)
                is Action.Dnd -> doDnd(context, resolved)
                is Action.Brightness -> doBrightness(context, resolved, ctx)
                is Action.AutoRotate -> doAutoRotate(context, resolved)
                is Action.ScreenTimeout -> doScreenTimeout(context, resolved, ctx)
                is Action.Dial -> doDial(context, routine, index, resolved, canLaunchActivity)
                is Action.SendSms -> doSendSms(context, routine, index, resolved, canLaunchActivity)
                is Action.GetLocation -> doGetLocation(context, resolved, ctx)
                is Action.Http -> doHttp(resolved, ctx)
                is Action.MediaKey -> doMediaKey(context, resolved)
                is Action.Wait -> doWait(resolved, allowWait, ctx)
                is Action.Clipboard -> doClipboard(context, resolved, source)
                is Action.TakePhoto -> doTakePhoto(context, resolved, fgsSwitch, ctx)
                is Action.BurstPhoto -> doBurstPhoto(context, resolved, fgsSwitch, ctx)
                is Action.RecordAudio -> doRecordAudio(context, resolved, fgsSwitch, ctx)
                is Action.PlaySound -> doPlaySound(context, resolved)
                is Action.SetAlarm -> doSetAlarm(context, routine, index, resolved, canLaunchActivity)
                is Action.Text -> doText(resolved, ctx)
                is Action.SetVariable -> doSetVariable(resolved, ctx)
                is Action.SetGlobalVariable -> doSetGlobalVariable(resolved, ctx)
                is Action.Calculate -> doCalculate(resolved, ctx)
                is Action.Expression -> doExpression(resolved, ctx)
                is Action.AskInput ->
                    doAskInput(context, routine, index, resolved, canLaunchActivity, ctx)

                is Action.ChooseMenu ->
                    doChooseMenu(context, routine, index, resolved, canLaunchActivity, ctx)
                // 流程控制標記與「執行程序」由直譯器 runProgram 處理；走到這裡不做事
                is Action.IfBegin, is Action.ElseIf, is Action.Else, is Action.EndIf,
                is Action.WhileBegin, is Action.EndWhile, is Action.RepeatBegin,
                is Action.EndRepeat, is Action.RunRoutine -> null
            }
            ActionResult(if (note == null) description else "$description（$note）", true)
        } catch (t: Throwable) {
            ActionResult(description, false, t.message ?: t.javaClass.simpleName)
        }
    }

    /**
     * 把動作的文字參數過一次變數解析，回傳代入後的動作。
     * 沒有文字參數或不含 token 的動作原樣回傳（[VariableResolver.resolve] 對純文字零改動）。
     * `設定變數` 的名稱是識別碼、不做解析，只解析它的值。
     */
    private fun resolveAction(action: Action, ctx: RunContext): Action = when (action) {
        is Action.Notify -> action.copy(
            title = VariableResolver.resolve(action.title, ctx),
            message = VariableResolver.resolve(action.message, ctx)
        )

        is Action.OpenUrl -> action.copy(url = VariableResolver.resolve(action.url, ctx))
        is Action.Share -> action.copy(text = VariableResolver.resolve(action.text, ctx))
        is Action.Speak -> action.copy(text = VariableResolver.resolve(action.text, ctx))
        is Action.Http -> action.copy(
            url = VariableResolver.resolve(action.url, ctx),
            body = VariableResolver.resolve(action.body, ctx)
        )

        is Action.Clipboard -> action.copy(text = VariableResolver.resolve(action.text, ctx))
        is Action.SetAlarm -> action.copy(label = VariableResolver.resolve(action.label, ctx))
        is Action.Dial -> action.copy(number = VariableResolver.resolve(action.number, ctx))
        is Action.SendSms -> action.copy(
            number = VariableResolver.resolve(action.number, ctx),
            message = VariableResolver.resolve(action.message, ctx)
        )

        is Action.Text -> action.copy(template = VariableResolver.resolve(action.template, ctx))
        is Action.SetVariable ->
            action.copy(template = VariableResolver.resolve(action.template, ctx))

        is Action.SetGlobalVariable ->
            action.copy(template = VariableResolver.resolve(action.template, ctx))

        is Action.Calculate -> action.copy(
            left = VariableResolver.resolve(action.left, ctx),
            right = VariableResolver.resolve(action.right, ctx)
        )

        // 互動動作：解析提示 / 選項 / 預設值；變數名稱是識別碼、不解析（同 SetVariable.name）
        is Action.AskInput -> action.copy(
            prompt = VariableResolver.resolve(action.prompt, ctx),
            defaultValue = VariableResolver.resolve(action.defaultValue, ctx)
        )

        is Action.ChooseMenu -> action.copy(
            prompt = VariableResolver.resolve(action.prompt, ctx),
            options = action.options.map { VariableResolver.resolve(it, ctx) }
        )

        else -> action
    }

    /**
     * 解析數值參數：[expr] 空 → 用原本的 [fallback]（舊資料 / 滑桿設定，行為不變）；
     * 否則先變數代入、再 parse 成整數（失敗回退 [fallback]），最後夾在安全 [range] 內。
     */
    private fun resolveNum(expr: String, fallback: Int, range: IntRange, ctx: RunContext): Int {
        if (expr.isBlank()) return fallback.coerceIn(range)
        val resolved = VariableResolver.resolve(expr, ctx)
        val n = resolved.trim().toIntOrNull() ?: fallback
        return n.coerceIn(range)
    }

    // ---------- 個別動作 ----------

    private fun doNotify(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.Notify
    ): String? {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            error("未授權通知權限，無法顯示通知")
        }
        val id = notificationId(routine.id, index)
        val notification = NotificationCompat.Builder(context, RoutinaApp.CHANNEL_ACTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(action.title.ifBlank { "Routina" })
            .setContentText(action.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(action.message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // 沒有更具體動作的通知,預設點了就打開 Routina,才知道這通知來自哪個程序
            .setContentIntent(openAppPendingIntent(context, id))
            .build()
        manager.notify(id, notification)
        return null
    }

    private fun doOpenApp(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.OpenApp,
        canLaunchActivity: Boolean
    ): String? {
        require(action.packageName.isNotBlank()) { "未選擇 App" }
        val label = action.appLabel.ifBlank { action.packageName }
        val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: error("找不到 App：$label")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchOrNotify(context, routine, index, intent, "開啟 $label", canLaunchActivity)
    }

    private fun doOpenUrl(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.OpenUrl,
        canLaunchActivity: Boolean
    ): String? {
        val normalized = normalizeUrl(action.url)
        // 擋掉危險 scheme：網址可能來自變數（如 {{通知內容}}），而通知內容是其他 App 可控的。
        // 只擋明確有害的 scheme，仍保留正常網址與 App deep link（tel:/mailto:/geo:/自訂 scheme）。
        requireSafeViewScheme(normalized)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchOrNotify(context, routine, index, intent, "開啟 $normalized", canLaunchActivity)
    }

    /**
     * 分享：以系統分享選單（ACTION_SEND + createChooser）把文字送出，由使用者當下選對象。
     * 沿用「開啟 App／網址」的背景 Activity 啟動處理——前景直接跳出選單、背景改發可點擊通知。
     * 文字為空時記失敗（無可分享內容），不崩潰。
     */
    private fun doShare(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.Share,
        canLaunchActivity: Boolean
    ): String? {
        require(action.text.isNotBlank()) { "沒有可分享的內容" }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, action.text)
        }
        // 由 application context 啟動 Activity（含 chooser）一律需要 NEW_TASK，比照開啟網址
        val chooser = Intent.createChooser(send, "分享")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchOrNotify(context, routine, index, chooser, "分享", canLaunchActivity, verb = "分享")
    }

    /**
     * 能直接啟動就直接啟動；否則改發一則可點擊[verb]的高優先度通知，
     * 並另發一則引導使用者授權「顯示在其他應用程式上層」的通知。
     *
     * [verb] 為通知文案的動詞（「開啟」/「分享」），讓開啟與分享共用同一條前景/背景分流。
     */
    private fun launchOrNotify(
        context: Context,
        routine: Routine,
        index: Int,
        intent: Intent,
        title: String,
        canLaunchActivity: Boolean,
        verb: String = "開啟"
    ): String? {
        if (canLaunchActivity) {
            context.startActivity(intent)
            return null
        }

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            error("背景無法直接啟動，且未授權通知權限")
        }
        val id = notificationId(routine.id, index)
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: error("無法建立啟動用的 PendingIntent")

        val notification = NotificationCompat.Builder(context, RoutinaApp.CHANNEL_LAUNCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("來自「${routine.name.ifBlank { "例行程序" }}」，點擊$verb")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(id, notification)
        notifyOverlayPermissionNeeded(context)
        return "已改以通知呈現，點擊$verb"
    }

    private fun doMediaVolume(context: Context, action: Action.MediaVolume, ctx: RunContext): String? {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: error("無法取得音訊服務")
        val stream = audioStream(action.stream)
        val max = audio.getStreamMaxVolume(stream)
        val percent = resolveNum(action.percentExpr, action.percent, Action.PERCENT_SAFE, ctx)
        val target = (max * percent / 100f).roundToInt().coerceIn(0, max)
        try {
            audio.setStreamVolume(stream, target, 0)
        } catch (t: SecurityException) {
            // 勿擾模式下調整鈴聲 / 通知音量需要勿擾模式存取權
            notifyDndPermissionNeeded(context)
            error("勿擾模式下需要勿擾模式存取權，已發送授權引導通知")
        }
        return null
    }

    private fun doRingerMode(context: Context, action: Action.RingerMode): String? {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: error("無法取得音訊服務")
        // 只有切換為靜音 / 震動需要「勿擾模式存取權」；切回正常模式直接嘗試即可
        if (action.mode != RingerModeType.NORMAL) {
            requireDndAccess(context, "切換響鈴模式")
        }
        val target = when (action.mode) {
            RingerModeType.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            RingerModeType.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            RingerModeType.SILENT -> AudioManager.RINGER_MODE_SILENT
        }
        try {
            audio.ringerMode = target
        } catch (t: Throwable) {
            // 部分機型即使切回正常模式也要求勿擾模式存取權
            notifyDndPermissionNeeded(context)
            error("切換響鈴模式失敗：${t.message ?: t.javaClass.simpleName}")
        }
        return null
    }

    /**
     * 藍牙開關。
     *
     * Android 13 起系統禁止第三方 App 直接切換藍牙，只能發通知把使用者帶到
     * 系統的確認對話框／設定頁——絕不記成假成功，描述會註明需經系統確認。
     */
    private fun doBluetooth(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.Bluetooth
    ): String? {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: error("此裝置不支援藍牙")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return requestBluetoothViaNotification(context, routine, index, action)
        }

        if (!hasBluetoothConnect(context)) {
            notifyBluetoothPermissionNeeded(context)
            error("缺少藍牙權限，已發送授權引導通知")
        }

        val started = try {
            @Suppress("DEPRECATION")
            if (action.enable) adapter.enable() else adapter.disable()
        } catch (t: SecurityException) {
            notifyBluetoothPermissionNeeded(context)
            error("缺少藍牙權限，已發送授權引導通知")
        }
        if (!started) error("系統拒絕切換藍牙")
        return null
    }

    /**
     * Wi-Fi 開關。
     *
     * Android 10 起系統禁止第三方 App 直接切換 Wi-Fi，只能把使用者帶到系統的 Wi-Fi 面板
     * （API 29+）或 Wi-Fi 設定頁自行切換——絕不記成假成功，描述會註明需自行切換。
     * 沿用「開啟 App／網址」的背景 Activity 啟動處理：前景直接開面板、背景改發可點擊通知。
     */
    private fun doWifiToggle(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.WifiToggle,
        canLaunchActivity: Boolean
    ): String? {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val title = if (action.on) "開啟 Wi-Fi" else "關閉 Wi-Fi"
        // 前景直接開面板時 launchOrNotify 回 null，補一句說明使用者仍要自己切換；背景則回它的通知說明
        return launchOrNotify(context, routine, index, intent, title, canLaunchActivity, verb = "切換 Wi-Fi")
            ?: "系統不允許 App 直接切換，已開啟 Wi-Fi 面板請自行切換"
    }

    /** 手電筒：取第一個有閃光燈的鏡頭（通常是主鏡頭） */
    private fun doFlashlight(context: Context, action: Action.Flashlight): String? {
        val manager = context.getSystemService(CameraManager::class.java)
            ?: error("無法取得相機服務")
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: error("此裝置沒有可用的手電筒")
        manager.setTorchMode(cameraId, action.on)
        return null
    }

    /** 朗讀文字：等到朗讀完成（或逾時 30 秒）才進行下一個動作 */
    private suspend fun doSpeak(context: Context, action: Action.Speak): String? {
        TtsSpeaker.speak(context, action.text.trim())
        return null
    }

    private fun doVibrate(context: Context, action: Action.Vibrate, ctx: RunContext): String? {
        val millis = resolveNum(action.millisExpr, action.millis, Action.VIBRATE_MS_SAFE, ctx)
            .toLong()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        } ?: error("此裝置沒有震動器")
        if (!vibrator.hasVibrator()) error("此裝置沒有震動器")
        vibrator.vibrate(
            VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE)
        )
        return null
    }

    /** 勿擾模式：開啟＝只允許優先通知，關閉＝全部通知 */
    private fun doDnd(context: Context, action: Action.Dnd): String? {
        val manager = requireDndAccess(context, "切換勿擾模式")
        manager.setInterruptionFilter(
            if (action.on) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY
            } else {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
        )
        return null
    }

    /**
     * 螢幕亮度：只寫亮度值，不動「自動亮度」旗標
     * （使用者開著自動亮度時，系統會在下一次環境光變化時接手，這是預期行為）。
     */
    private fun doBrightness(context: Context, action: Action.Brightness, ctx: RunContext): String? {
        if (!canWriteSettings(context)) {
            notifyWriteSettingsNeeded(context)
            error("缺少「修改系統設定」權限，已發送授權引導通知")
        }
        val percent = resolveNum(action.percentExpr, action.percent, Action.PERCENT_SAFE, ctx)
        val value = (MAX_BRIGHTNESS * percent / 100f)
            .roundToInt()
            .coerceIn(0, MAX_BRIGHTNESS)
        val written = Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            value
        )
        if (!written) error("系統拒絕寫入亮度設定")
        return null
    }

    /** 自動旋轉：寫入 ACCELEROMETER_ROTATION（1/0），權限處理同螢幕亮度 */
    private fun doAutoRotate(context: Context, action: Action.AutoRotate): String? {
        if (!canWriteSettings(context)) {
            notifyWriteSettingsNeeded(context)
            error("缺少「修改系統設定」權限，已發送授權引導通知")
        }
        val written = Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            if (action.on) 1 else 0
        )
        if (!written) error("系統拒絕寫入自動旋轉設定")
        return null
    }

    /** 螢幕逾時：系統設定以毫秒儲存，這裡收秒數（夾在安全範圍內）再換算 */
    private fun doScreenTimeout(
        context: Context,
        action: Action.ScreenTimeout,
        ctx: RunContext
    ): String? {
        if (!canWriteSettings(context)) {
            notifyWriteSettingsNeeded(context)
            error("缺少「修改系統設定」權限，已發送授權引導通知")
        }
        val seconds = resolveNum(
            action.secondsExpr, action.seconds, Action.SCREEN_TIMEOUT_SAFE, ctx
        )
        val written = Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_OFF_TIMEOUT,
            seconds * 1000
        )
        if (!written) error("系統拒絕寫入螢幕逾時設定")
        return null
    }

    /**
     * 撥號：帶號碼開啟系統撥號畫面（ACTION_DIAL），不自動撥出、不需要通話權限。
     * 沿用「開啟 App／網址」的背景 Activity 啟動處理——前景直接開，背景改發可點擊通知。
     */
    private fun doDial(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.Dial,
        canLaunchActivity: Boolean
    ): String? {
        val number = action.number.trim()
        require(number.isNotBlank()) { "未輸入電話號碼" }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchOrNotify(
            context, routine, index, intent, "撥號 $number", canLaunchActivity, verb = "撥號"
        )
    }

    /**
     * 傳簡訊：開啟簡訊 App 並填好收件人與內容（ACTION_SENDTO），由使用者自己按送出。
     * 不使用 SEND_SMS 權限直接發訊；背景啟動限制的處理同撥號。
     */
    private fun doSendSms(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.SendSms,
        canLaunchActivity: Boolean
    ): String? {
        val number = action.number.trim()
        require(number.isNotBlank()) { "未輸入收件號碼" }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
            .putExtra("sms_body", action.message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launchOrNotify(
            context, routine, index, intent, "傳簡訊給 $number", canLaunchActivity, verb = "編輯簡訊"
        )
    }

    /**
     * 取得目前位置：要一次當下座標存進具名變數。
     *
     * 用 PRIORITY_BALANCED_POWER_ACCURACY 而非最高精度：例行程序要的是「我人在哪」，
     * 不值得為幾公尺差距開 GPS。缺權限、定位關閉或逾時都記為失敗——
     * 絕不拿舊的快取位置冒充當下位置。
     */
    private suspend fun doGetLocation(
        context: Context,
        action: Action.GetLocation,
        ctx: RunContext
    ): String {
        val name = action.variableName.trim()
        if (name.isBlank()) error("未設定要存入的變數名稱")
        if (!GeofenceManager.hasForegroundLocation(context)) {
            error("未授權位置權限，請到系統設定允許 Routina 存取位置")
        }
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) { currentLocation(context) }
            ?: error("取不到目前位置（逾時或定位關閉）")
        val value = when (action.format) {
            LocationFormat.LAT -> formatCoordinate(location.latitude)
            LocationFormat.LNG -> formatCoordinate(location.longitude)
            LocationFormat.LAT_LNG ->
                "${formatCoordinate(location.latitude)},${formatCoordinate(location.longitude)}"
        }
        ctx.vars[name] = value
        return "$name = $value"
    }

    /**
     * 把 Play Services 的 getCurrentLocation（Task）接成 suspend 函式。
     *
     * 專案沒有 kotlinx-coroutines-play-services，為了一個呼叫多帶一個相依不划算，
     * 直接掛三個 listener 即可；取不到位置一律回 null，由呼叫端記為失敗。
     */
    private suspend fun currentLocation(context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            val cancellation = CancellationTokenSource()
            cont.invokeOnCancellation { runCatching { cancellation.cancel() } }
            try {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        cancellation.token
                    )
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                    .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
            } catch (t: Throwable) {
                // 沒有 GMS、或權限在呼叫瞬間被撤銷：當成取不到位置，不讓它變成崩潰
                if (cont.isActive) cont.resume(null)
            }
        }

    /** 經緯度輸出固定 6 位小數（約 0.1 公尺解析度，足夠且不帶浮點雜訊） */
    private fun formatCoordinate(value: Double): String =
        String.format(java.util.Locale.US, "%.6f", value)

    /**
     * HTTP 請求（webhook）：連線 / 讀取各 10 秒逾時，2xx 視為成功。
     * 2xx 的回應內容存入 [RunContext.lastOutput]，後續動作可用 `{{result}}` 引用
     * （過長時截斷，避免情境無限膨脹）。
     */
    private suspend fun doHttp(action: Action.Http, ctx: RunContext): String =
        withContext(Dispatchers.IO) {
            val normalized = normalizeUrl(action.url)
            val method = if (action.method.equals(Action.METHOD_POST, ignoreCase = true)) {
                Action.METHOD_POST
            } else {
                Action.METHOD_GET
            }

            val connection = (URL(normalized).openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                requestMethod = method
                instanceFollowRedirects = true
            }
            try {
                if (method == Action.METHOD_POST) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                    connection.outputStream.use { it.write(action.body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                // 讀完回應（必須讀完或關閉才能讓連線正確回收）；成功時保留為輸出
                val body = runCatching {
                    (if (code in 200..299) connection.inputStream else connection.errorStream)
                        ?.use { it.readBytes().toString(Charsets.UTF_8) }
                }.getOrNull()
                if (code !in 200..299) error("HTTP $code")
                ctx.lastOutput = body?.take(MAX_HTTP_OUTPUT_CHARS) ?: ""
                "HTTP $code"
            } finally {
                runCatching { connection.disconnect() }
            }
        }

    /** 播放控制：送出成對的媒體按鍵事件（按下 + 放開） */
    private fun doMediaKey(context: Context, action: Action.MediaKey): String? {
        val audio = context.getSystemService(AudioManager::class.java)
            ?: error("無法取得音訊服務")
        val keyCode = when (action.key) {
            Action.KEY_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            Action.KEY_PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        return null
    }

    /** 等待：只有在前景執行服務或手動執行時才真的等 */
    private suspend fun doWait(action: Action.Wait, allowWait: Boolean, ctx: RunContext): String? {
        if (!allowWait) return "前景執行服務不可用，已跳過等待"
        val seconds = resolveNum(action.secondsExpr, action.seconds, Action.WAIT_SECONDS_SAFE, ctx)
        delay(seconds * 1000L)
        return null
    }

    /**
     * 複製到剪貼簿。
     *
     * Android 10 起系統只允許前景（或擁有輸入法／無障礙身分）的 App 寫入剪貼簿。
     * 背景寫入被擋下時系統**不會丟例外也不會回報**，完全無從偵測，
     * 所以這裡的策略是誠實面對：手動執行（App 在前景）一律生效；
     * 背景觸發照常呼叫並記為成功，但在描述加註可能被系統忽略——
     * 絕不宣稱「一定寫進去了」，也不因為偵測不到就記成失敗。
     * API 33 起系統會顯示「已複製」浮層，使用者自己就能確認。
     */
    private fun doClipboard(
        context: Context,
        action: Action.Clipboard,
        source: TriggerSource
    ): String? {
        val manager = context.getSystemService(ClipboardManager::class.java)
            ?: error("無法取得剪貼簿服務")
        manager.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, action.text))
        return if (source == TriggerSource.MANUAL) null else BACKGROUND_CLIPBOARD_NOTE
    }

    /**
     * 拍照。
     *
     * Android 9+ 禁止背景存取相機：手動執行（App 在前景、fgsSwitch 為 null）一律嘗試；
     * 背景觸發先請前景服務切到 camera 類型，被系統擋下（Android 14+ 背景啟動限制）就記為
     * 受限失敗並誠實註明——絕不崩潰。未授權相機權限則記失敗並發引導通知。
     */
    private suspend fun doTakePhoto(
        context: Context,
        action: Action.TakePhoto,
        fgsSwitch: ForegroundTypeSwitch?,
        ctx: RunContext
    ): String {
        requireCameraPermission(context)
        requireCaptureForeground(fgsSwitch, CaptureFgsType.CAMERA, "相機")
        return try {
            val result = CameraCapture.capture(
                context, action.lensBack, count = 1, intervalMs = 0L,
                shareToGallery = action.shareToGallery
            )
            if (result.uris.isEmpty()) error("擷取失敗")
            // 相片的 content URI 設為輸出，後續動作可用 {{result}} 引用
            ctx.lastOutput = result.uris.lastOrNull()?.toString() ?: ""
            if (action.notify) {
                notifyCaptureResult(
                    context = context,
                    uri = result.uris.lastOrNull(),
                    mimeType = "image/*",
                    title = "已拍照",
                    fileName = result.displayName
                )
            }
            captureStorageNote(action.shareToGallery)
        } finally {
            fgsSwitch?.restore()
        }
    }

    /** 連拍：同一次相機綁定內迴圈擷取 count 張、每張間隔 intervalMs（規則同拍照） */
    private suspend fun doBurstPhoto(
        context: Context,
        action: Action.BurstPhoto,
        fgsSwitch: ForegroundTypeSwitch?,
        ctx: RunContext
    ): String {
        requireCameraPermission(context)
        requireCaptureForeground(fgsSwitch, CaptureFgsType.CAMERA, "相機")
        val count = resolveNum(action.countExpr, action.count, Action.BURST_COUNT_SAFE, ctx)
        val interval = resolveNum(action.intervalExpr, action.intervalMs, Action.BURST_INTERVAL_SAFE, ctx)
        return try {
            val result = CameraCapture.capture(
                context, action.lensBack, count, interval.toLong(),
                shareToGallery = action.shareToGallery
            )
            if (result.uris.isEmpty()) error("擷取失敗")
            // 最後一張的 content URI 設為輸出
            ctx.lastOutput = result.uris.lastOrNull()?.toString() ?: ""
            if (action.notify) {
                notifyCaptureResult(
                    context = context,
                    uri = result.uris.lastOrNull(),
                    mimeType = "image/*",
                    title = "已連拍 ${result.uris.size} 張",
                    fileName = result.displayName
                )
            }
            "${captureStorageNote(action.shareToGallery)}（${result.uris.size} 張）"
        } finally {
            fgsSwitch?.restore()
        }
    }

    /**
     * 錄音。
     *
     * 麥克風與相機受相同的前景限制。手動一律嘗試；背景切到 microphone 類型失敗即記受限失敗。
     * 太短或無音源時 MediaRecorder 內部已吞掉 stop 例外，仍會存出（可能空的）檔案。
     */
    private suspend fun doRecordAudio(
        context: Context,
        action: Action.RecordAudio,
        fgsSwitch: ForegroundTypeSwitch?,
        ctx: RunContext
    ): String {
        requireRecordPermission(context)
        requireCaptureForeground(fgsSwitch, CaptureFgsType.MICROPHONE, "麥克風")
        val seconds = resolveNum(action.secondsExpr, action.seconds, Action.RECORD_SECONDS_SAFE, ctx)
        return try {
            val result = AudioRecorder.record(context, seconds, action.shareToGallery)
            // 音檔的 content URI 設為輸出（可能為 null，代空字串）
            ctx.lastOutput = result.uri?.toString() ?: ""
            if (action.notify) {
                notifyCaptureResult(
                    context = context,
                    uri = result.uri,
                    mimeType = "audio/*",
                    title = "已錄音 $seconds 秒",
                    fileName = result.displayPath.substringAfterLast('/')
                )
            }
            "已存到 ${result.displayPath}"
        } finally {
            fgsSwitch?.restore()
        }
    }

    /** 播放系統預設音效（通知音／鬧鐘聲／鈴聲）；無額外權限需求 */
    private fun doPlaySound(context: Context, action: Action.PlaySound): String? {
        val ringtoneType = when (action.type) {
            Action.SOUND_ALARM -> RingtoneManager.TYPE_ALARM
            Action.SOUND_RINGTONE -> RingtoneManager.TYPE_RINGTONE
            else -> RingtoneManager.TYPE_NOTIFICATION
        }
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, ringtoneType)
            ?: RingtoneManager.getDefaultUri(ringtoneType)
            ?: error("找不到系統預設音效")
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: error("無法播放系統音效")
        ringtone.play()
        return null
    }

    /**
     * 設定鬧鐘：以系統時鐘 App 建立一個鬧鐘（SKIP_UI 免使用者確認）。
     * 沿用「開啟 App／網址」的背景 Activity 啟動處理——前景直接送、背景改發可點擊的通知。
     * 沒有可處理的時鐘 App 時記失敗，不崩潰。
     */
    private fun doSetAlarm(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.SetAlarm,
        canLaunchActivity: Boolean
    ): String? {
        val hour = action.hour.coerceIn(0, 23)
        val minute = action.minute.coerceIn(0, 59)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (action.label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) == null) {
            error("找不到可設定鬧鐘的時鐘 App")
        }
        val title = "設定鬧鐘 %02d:%02d".format(hour, minute)
        return launchOrNotify(context, routine, index, intent, title, canLaunchActivity)
    }

    /** 文字：把（已代入變數的）文字設為輸出，供後續動作以 {{result}} 引用 */
    private fun doText(action: Action.Text, ctx: RunContext): String? {
        ctx.lastOutput = action.template
        return null
    }

    /** 設定變數：把（已代入變數的）值存成具名變數，供後續以 {{var:名稱}} 引用 */
    private fun doSetVariable(action: Action.SetVariable, ctx: RunContext): String? {
        val name = action.name.trim()
        if (name.isBlank()) error("未設定變數名稱")
        ctx.vars[name] = action.template
        return null
    }

    /**
     * 設定全域變數：把（已代入變數的）值寫入全域情境並登記異動，
     * 執行結束時由 [execute] 落地保存，供任何程序以 {{全域:名稱}} 引用。
     */
    private fun doSetGlobalVariable(action: Action.SetGlobalVariable, ctx: RunContext): String? {
        val name = action.name.trim()
        if (name.isBlank()) error("未設定全域變數名稱")
        ctx.globals[name] = action.template
        ctx.dirtyGlobals += name
        return null
    }

    /** 計算：把（已代入變數的）left op right 算完存進具名變數；非數字或除以 0 記為失敗 */
    private fun doCalculate(action: Action.Calculate, ctx: RunContext): String? {
        val name = action.name.trim()
        if (name.isBlank()) error("未設定要存入的變數名稱")
        val a = action.left.trim().toDoubleOrNull() ?: error("左邊不是數字：「${action.left}」")
        val b = action.right.trim().toDoubleOrNull() ?: error("右邊不是數字：「${action.right}」")
        val result = when (action.op) {
            MathOp.ADD -> a + b
            MathOp.SUBTRACT -> a - b
            MathOp.MULTIPLY -> a * b
            MathOp.DIVIDE -> if (b == 0.0) error("不能除以 0") else a / b
            MathOp.MODULO -> if (b == 0.0) error("不能對 0 取餘數") else a % b
        }
        val text = formatNumber(result)
        ctx.vars[name] = text
        return "$name = $text"
    }

    /** 運算式：解析並求值一行「名稱 = 值」，存進具名變數 */
    private fun doExpression(action: Action.Expression, ctx: RunContext): String? {
        val (name, value) = ExpressionEval.evaluate(action.text, ctx)
        ctx.vars[name] = value
        return "$name = $value"
    }

    /**
     * 詢問輸入：暫停並跳出對話框請使用者輸入文字，答案存進具名變數。
     * 使用者取消 / 逾時未回應時記為失敗（不影響其餘動作）。
     */
    private suspend fun doAskInput(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.AskInput,
        canLaunchActivity: Boolean,
        ctx: RunContext
    ): String {
        val name = action.variableName.trim()
        if (name.isBlank()) error("未設定要存入的變數名稱")
        val answer = awaitUserInput(
            context, routine, index,
            kind = InputPromptActivity.KIND_INPUT,
            prompt = action.prompt,
            default = action.defaultValue,
            options = emptyList(),
            variableName = name,
            canLaunchActivity = canLaunchActivity
        ) ?: error("使用者未回應／已取消")
        ctx.vars[name] = answer
        return "$name = $answer"
    }

    /**
     * 選單選擇：暫停並跳出對話框列出選項讓使用者選一個，選中的文字存進具名變數。
     * 沒有可選選項時記為失敗；使用者取消 / 逾時未回應時記為失敗。
     */
    private suspend fun doChooseMenu(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.ChooseMenu,
        canLaunchActivity: Boolean,
        ctx: RunContext
    ): String {
        val name = action.variableName.trim()
        if (name.isBlank()) error("未設定要存入的變數名稱")
        val options = action.options.filter { it.isNotBlank() }
        if (options.isEmpty()) error("選單沒有任何選項")
        val chosen = awaitUserInput(
            context, routine, index,
            kind = InputPromptActivity.KIND_MENU,
            prompt = action.prompt,
            default = "",
            options = options,
            variableName = name,
            canLaunchActivity = canLaunchActivity
        ) ?: error("使用者未回應／已取消")
        ctx.vars[name] = chosen
        return "$name = $chosen"
    }

    /**
     * 互動動作共用的等待流程：登記一筆請求 → 啟動（或背景以通知帶出）[InputPromptActivity]
     * → 在 deferred 上掛起等待使用者回應。前景（手動 / 有覆蓋權限）直接跳對話框；背景改發
     * 可點擊通知，點了才開對話框——沿用「開啟 App／網址」的 [launchOrNotify] 降級精神。
     *
     * 一律帶等待上限（[INPUT_TIMEOUT_MS]），使用者一直不回應時整條例行程序不會永遠卡住。
     * 回傳答案字串；取消 / 逾時回傳 null，由呼叫端記為失敗。
     */
    private suspend fun awaitUserInput(
        context: Context,
        routine: Routine,
        index: Int,
        kind: String,
        prompt: String,
        default: String,
        options: List<String>,
        variableName: String,
        canLaunchActivity: Boolean
    ): String? {
        val requestId = InputBridge.nextRequestId()
        val deferred = InputBridge.open(requestId)
        val intent = InputPromptActivity.intent(
            context, requestId, kind, prompt, default, options, variableName
        )
        return try {
            // 前景直接 startActivity；背景無法直接啟動時改發通知（點擊才開對話框），仍在此等待
            launchOrNotify(context, routine, index, intent, promptTitle(kind), canLaunchActivity, verb = "回答")
            withTimeoutOrNull(INPUT_TIMEOUT_MS) { deferred.await() }
        } finally {
            // 逾時或啟動失敗時清掉登記，避免 pending 洩漏
            InputBridge.cancel(requestId)
        }
    }

    private fun promptTitle(kind: String): String =
        if (kind == InputPromptActivity.KIND_MENU) "選單選擇" else "詢問輸入"

    /** 整數結果去掉小數點；非整數保留（去尾零），四捨五入到 6 位避免浮點雜訊 */
    private fun formatNumber(d: Double): String = when {
        d.isNaN() || d.isInfinite() -> "0"
        d % 1.0 == 0.0 -> d.toLong().toString()
        else -> String.format(java.util.Locale.US, "%.6f", d).trimEnd('0').trimEnd('.')
    }

    /** 相機權限檢查；未授權時發引導通知並中止這個動作 */
    private fun requireCameraPermission(context: Context) {
        if (!hasSelfPermission(context, Manifest.permission.CAMERA)) {
            notifyCameraPermissionNeeded(context)
            error("未授權相機權限，已發送授權引導通知")
        }
    }

    /** 麥克風權限檢查；未授權時發引導通知並中止這個動作 */
    private fun requireRecordPermission(context: Context) {
        if (!hasSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
            notifyMicrophonePermissionNeeded(context)
            error("未授權麥克風權限，已發送授權引導通知")
        }
    }

    /**
     * 確認能存取相機／麥克風的前景身分。
     *
     * fgsSwitch 為 null＝手動執行、App 在前景，直接放行；非 null＝背景觸發，
     * 請前景服務切到對應類型，被系統擋下（[ForegroundTypeSwitch.switchTo] 回傳 false）就中止並註明。
     */
    private fun requireCaptureForeground(
        fgsSwitch: ForegroundTypeSwitch?,
        type: CaptureFgsType,
        hardware: String
    ) {
        if (fgsSwitch == null) return
        if (!fgsSwitch.switchTo(type)) {
            error("背景無法啟動$hardware，請改用手動執行或前景觸發")
        }
    }

    private fun hasSelfPermission(context: Context, permission: String): Boolean =
        runCatching {
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    /**
     * Android 13+ 的降級路徑：開啟且已授權 BLUETOOTH_CONNECT 時帶出系統確認對話框，
     * 其餘情況（關閉、或未授權）導向藍牙設定頁。
     */
    private fun requestBluetoothViaNotification(
        context: Context,
        routine: Routine,
        index: Int,
        action: Action.Bluetooth
    ): String {
        // ACTION_REQUEST_ENABLE 本身需要 BLUETOOTH_CONNECT，未授權時點了會失敗 → 直接導設定頁
        val useSystemDialog = action.enable && hasBluetoothConnect(context)
        val intent = if (useSystemDialog) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        } else {
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            error("Android 13+ 無法直接切換藍牙，且未授權通知權限")
        }
        val id = notificationId(routine.id, index)
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: error("無法建立藍牙用的 PendingIntent")

        val title = if (action.enable) "開啟藍牙" else "關閉藍牙"
        val notification = NotificationCompat.Builder(context, RoutinaApp.CHANNEL_LAUNCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(
                "來自「${routine.name.ifBlank { "例行程序" }}」，" +
                    if (useSystemDialog) "點擊確認開啟" else "點擊前往藍牙設定"
            )
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(id, notification)

        return if (useSystemDialog) {
            "Android 13+ 需經系統確認，已發通知，點擊確認開啟"
        } else {
            "Android 13+ 無法直接切換，已發通知，點擊前往藍牙設定"
        }
    }

    /** Android 12 起切換藍牙需要 BLUETOOTH_CONNECT；之前為安裝時授予的舊權限 */
    private fun hasBluetoothConnect(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return runCatching {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /** 取得通知服務並確認已授權勿擾模式存取權；未授權時發引導通知並中止這個動作 */
    private fun requireDndAccess(context: Context, purpose: String): NotificationManager {
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: error("無法取得通知服務")
        if (!manager.isNotificationPolicyAccessGranted) {
            notifyDndPermissionNeeded(context)
            error("$purpose 需要勿擾模式存取權，已發送授權引導通知")
        }
        return manager
    }

    /** 是否可寫入系統設定（螢幕亮度動作的前提） */
    fun canWriteSettings(context: Context): Boolean =
        runCatching { Settings.System.canWrite(context) }.getOrDefault(false)

    // ---------- 權限引導通知 ----------

    private fun notifyBluetoothPermissionNeeded(context: Context) {
        val settingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        notifyPermissionNeeded(
            context = context,
            notificationId = NOTIFY_ID_BLUETOOTH,
            requestCode = REQUEST_BLUETOOTH_SETTINGS,
            intent = settingsIntent,
            title = "需要「附近的裝置」權限",
            text = "點此前往設定，允許 Routina 切換藍牙"
        )
    }

    private fun notifyDndPermissionNeeded(context: Context) {
        val settingsIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        notifyPermissionNeeded(
            context = context,
            notificationId = NOTIFY_ID_DND,
            requestCode = REQUEST_DND_SETTINGS,
            intent = settingsIntent,
            title = "需要「勿擾模式存取權」",
            text = "點此前往設定，允許 Routina 切換響鈴與勿擾模式"
        )
    }

    private fun notifyOverlayPermissionNeeded(context: Context) {
        val settingsIntent = directOrGeneric(
            context,
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION
        )
        notifyPermissionNeeded(
            context = context,
            notificationId = NOTIFY_ID_OVERLAY,
            requestCode = REQUEST_OVERLAY_SETTINGS,
            intent = settingsIntent,
            title = "需要「顯示在其他應用程式上層」",
            text = "點此前往設定，讓背景觸發能直接開啟 App 或網址；若無法開啟請先允許受限制的設定"
        )
    }

    private fun notifyWriteSettingsNeeded(context: Context) {
        val settingsIntent = directOrGeneric(context, Settings.ACTION_MANAGE_WRITE_SETTINGS)
        notifyPermissionNeeded(
            context = context,
            notificationId = NOTIFY_ID_WRITE_SETTINGS,
            requestCode = REQUEST_WRITE_SETTINGS,
            intent = settingsIntent,
            title = "需要「修改系統設定」權限",
            text = "點此前往設定，允許 Routina 調整螢幕亮度、自動旋轉與螢幕逾時"
        )
    }

    private fun notifyCameraPermissionNeeded(context: Context) {
        val settingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        notifyPermissionNeeded(
            context = context,
            notificationId = NOTIFY_ID_CAMERA,
            requestCode = REQUEST_CAMERA_SETTINGS,
            intent = settingsIntent,
            title = "需要「相機」權限",
            text = "點此前往設定，允許 Routina 拍照"
        )
    }

    private fun notifyMicrophonePermissionNeeded(context: Context) {
        val settingsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        notifyPermissionNeeded(
            context = context,
            notificationId = NOTIFY_ID_MICROPHONE,
            requestCode = REQUEST_MICROPHONE_SETTINGS,
            intent = settingsIntent,
            title = "需要「麥克風」權限",
            text = "點此前往設定，允許 Routina 錄音"
        )
    }

    /**
     * 帶 package URI 可直達本 App 的開關頁（部分機型的通用清單頁很難找到自己的 App）；
     * 該頁不存在時退回通用清單頁，避免通知點了沒反應。
     */
    private fun directOrGeneric(context: Context, action: String): Intent {
        val direct = Intent(action, Uri.parse("package:${context.packageName}"))
        val resolvable = runCatching {
            direct.resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
        return (if (resolvable) direct else Intent(action))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun notifyPermissionNeeded(
        context: Context,
        notificationId: Int,
        requestCode: Int,
        intent: Intent,
        title: String,
        text: String
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val notification = NotificationCompat.Builder(context, RoutinaApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(notificationId, notification)
    }

    // ---------- 擷取結果通知 ----------

    /**
     * 擷取成功後發一則可點擊開啟的結果通知。
     *
     * 點擊以系統檢視器（相片檢視器 / 音訊播放器）開啟 [uri]：ACTION_VIEW + content URI +
     * FLAG_GRANT_READ_URI_PERMISSION 讓外部 App 讀得到、PendingIntent 帶 FLAG_IMMUTABLE。
     * 通知 ID 以流水號遞增，連拍 / 多次擷取的通知不會互相覆蓋。
     * 未授權通知（Android 13+）時靜默略過——擷取動作本身仍記為成功（檔案已存）。
     */
    private fun notifyCaptureResult(
        context: Context,
        uri: Uri?,
        mimeType: String,
        title: String,
        fileName: String
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val id = CAPTURE_NOTIFY_SEQ.getAndIncrement()
        val builder = NotificationCompat.Builder(context, RoutinaApp.CHANNEL_CAPTURE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(fileName)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (uri != null && uri != Uri.EMPTY) {
            val viewIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mimeType)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                )
            val pending = PendingIntent.getActivity(
                context,
                id,
                viewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) builder.setContentIntent(pending)
        }
        manager.notify(id, builder.build())
    }

    /** 擷取存檔位置的紀錄說明：預設 App 私有,只有選了「存到公開相簿」才進公開空間 */
    private fun captureStorageNote(shareToGallery: Boolean): String =
        if (shareToGallery) "已存入公開相簿（其他 App 可讀）" else "已存入 App 私有空間"

    // ---------- 共用工具 ----------

    /**
     * 開啟 Routina App（launcher / MainActivity）的 PendingIntent，
     * 給沒有更具體點擊動作的通知當預設行為——點了至少會進到 App，不會「點了沒反應」。
     * 取不到啟動 intent（極少見）時回 null，通知就維持無點擊動作、不崩潰。
     */
    private fun openAppPendingIntent(context: Context, requestCode: Int): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return null
        return PendingIntent.getActivity(
            context,
            requestCode,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 是否具備「顯示在其他應用程式上層」權限（背景啟動 Activity 的前提） */
    fun canDrawOverlays(context: Context): Boolean =
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    // ---------- 觸發情境值 ----------

    /** 所有執行都可用的常用情境值：當下時間、日期、星期、電量 */
    private fun commonTriggerValues(context: Context): Map<String, String> {
        val now = System.currentTimeMillis()
        val values = mutableMapOf(
            "時間" to formatClock("HH:mm", now),
            "日期" to formatClock("yyyy-MM-dd", now),
            "星期" to weekdayName(now)
        )
        batteryPercent(context)?.let { values["電量"] = it.toString() }
        return values
    }

    /**
     * 由 routine 自身的觸發設定推得的情境值（地點 / 標籤 / 網路 / 裝置名稱、通知來源 App）。
     * 這些是設定時就已知的「名稱」；當次觸發若帶來更即時的實際值，會在 execute 覆寫這裡的預設。
     */
    private fun triggerContextFromRoutine(trigger: Trigger): Map<String, String> = buildMap {
        when (trigger) {
            is Trigger.LocationEnter -> if (trigger.label.isNotBlank()) put("地點名稱", trigger.label)
            is Trigger.LocationExit -> if (trigger.label.isNotBlank()) put("地點名稱", trigger.label)
            is Trigger.NfcTag -> if (trigger.label.isNotBlank()) put("標籤名稱", trigger.label)
            is Trigger.WifiConnected -> if (trigger.ssid.isNotBlank()) put("Wi-Fi名稱", trigger.ssid)
            is Trigger.BtConnected ->
                if (trigger.deviceName.isNotBlank()) put("藍牙裝置", trigger.deviceName)

            is Trigger.BtDisconnected ->
                if (trigger.deviceName.isNotBlank()) put("藍牙裝置", trigger.deviceName)

            is Trigger.NotificationPosted ->
                if (trigger.appName.isNotBlank()) put("通知來源App", trigger.appName)

            else -> Unit
        }
    }

    private fun formatClock(pattern: String, timeMs: Long): String =
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
            .format(java.util.Date(timeMs))

    /** 星期：週一 … 週日（Calendar 的 DAY_OF_WEEK 以週日為 1） */
    private fun weekdayName(timeMs: Long): String {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timeMs }
        val labels = arrayOf("日", "一", "二", "三", "四", "五", "六")
        val index = (calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
        return "週" + labels[index]
    }

    private fun batteryPercent(context: Context): Int? = runCatching {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
    }.getOrNull()

    /** 已帶 scheme（http:、mailto:、myapp: …）就原樣使用，否則補上 https:// */
    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotBlank()) { "網址是空的" }
        return if (URL_SCHEME.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
    }

    /**
     * 以 ACTION_VIEW 開啟前的安全檢查：擋掉可被濫用的 scheme。
     * file:/content: 可能外洩本機檔案、javascript:/data: 可在瀏覽器情境執行、
     * intent:/android-app: 可被用來繞道啟動其他元件——這些都不是「開啟網址」的正當用途。
     */
    private fun requireSafeViewScheme(url: String) {
        val scheme = URL_SCHEME.find(url)?.value?.removeSuffix(":")?.lowercase()
        if (scheme != null && scheme in UNSAFE_VIEW_SCHEMES) {
            error("基於安全，不開啟 $scheme: 開頭的網址")
        }
    }

    private fun audioStream(stream: VolumeStream): Int = when (stream) {
        VolumeStream.MEDIA -> AudioManager.STREAM_MUSIC
        VolumeStream.RING -> AudioManager.STREAM_RING
        VolumeStream.ALARM -> AudioManager.STREAM_ALARM
        VolumeStream.NOTIFICATION -> AudioManager.STREAM_NOTIFICATION
    }

    /**
     * 由 routine id + 動作序號推導通知 ID：同一動作重複觸發會更新同一則通知，
     * 不同動作彼此不覆蓋，且必定落在 [1000, 8999] 內、避開固定 ID。
     */
    private fun notificationId(routineId: String, actionIndex: Int): Int {
        val raw = (routineId.hashCode() * 31 + actionIndex) and 0x7FFFFFFF
        return NOTIFY_ID_MIN + raw % NOTIFY_ID_RANGE
    }

    // ---------- 顯示文字 ----------

    fun describe(context: Context, action: Action): String = when (action) {
        is Action.Notify -> "顯示通知：${action.title.ifBlank { "(無標題)" }}"
        is Action.OpenApp -> "開啟 App：${action.appLabel.ifBlank { action.packageName }}"
        is Action.OpenUrl -> "開啟網址：${redactUrl(action.url, stripQuery = false)}"
        is Action.Share -> "分享：${redactText(action.text)}"
        is Action.MediaVolume ->
            "${volumeStreamLabel(action.stream)}音量：${numLabel(action.percentExpr, action.percent)}%"

        is Action.RingerMode -> "響鈴模式：${ringerLabel(action.mode)}"
        is Action.Bluetooth -> "藍牙：${if (action.enable) "開啟" else "關閉"}"
        is Action.WifiToggle -> "Wi-Fi：${if (action.on) "開啟" else "關閉"}"
        is Action.Flashlight -> "手電筒：${if (action.on) "開啟" else "關閉"}"
        is Action.Speak -> "朗讀文字：${redactText(action.text)}"
        is Action.Vibrate -> "震動：${numLabel(action.millisExpr, action.millis)} 毫秒"
        is Action.Dnd -> "勿擾模式：${if (action.on) "開啟" else "關閉"}"
        is Action.Brightness -> "螢幕亮度：${numLabel(action.percentExpr, action.percent)}%"
        is Action.AutoRotate -> "自動旋轉：${if (action.on) "開啟" else "關閉"}"
        is Action.ScreenTimeout -> "螢幕逾時：${numLabel(action.secondsExpr, action.seconds)} 秒"
        is Action.Dial -> "撥號：${redactText(action.number)}"
        is Action.SendSms -> "傳簡訊：${redactText(action.number)}"
        is Action.GetLocation ->
            "取得目前位置：存到 ${action.variableName.ifBlank { "(未命名)" }}"

        is Action.Http -> "HTTP ${action.method}：${redactUrl(action.url, stripQuery = true)}"
        is Action.MediaKey -> "播放控制：${mediaKeyLabel(action.key)}"
        is Action.Wait -> "等待 ${numLabel(action.secondsExpr, action.seconds)} 秒"
        is Action.Clipboard -> "複製到剪貼簿：${redactText(action.text)}"
        is Action.TakePhoto -> "拍照：${lensLabel(action.lensBack)}"
        is Action.BurstPhoto ->
            "連拍：${lensLabel(action.lensBack)}、${numLabel(action.countExpr, action.count)} 張、" +
                "間隔 ${numLabel(action.intervalExpr, action.intervalMs)}ms"

        is Action.RecordAudio -> "錄音：${numLabel(action.secondsExpr, action.seconds)} 秒"
        is Action.PlaySound -> "播放音效：${soundTypeLabel(action.type)}"
        is Action.SetAlarm -> {
            val time = "%02d:%02d".format(action.hour, action.minute)
            if (action.label.isBlank()) "設定鬧鐘：$time" else "設定鬧鐘：$time（${action.label}）"
        }

        is Action.Text -> "文字：${redactText(action.template)}"
        is Action.SetVariable ->
            "設定變數 ${action.name.ifBlank { "(未命名)" }}：${redactText(action.template)}"

        is Action.SetGlobalVariable ->
            "設定全域變數 ${action.name.ifBlank { "(未命名)" }}：${redactText(action.template)}"

        is Action.Calculate ->
            "計算 ${action.name.ifBlank { "(未命名)" }} = ${action.left} ${mathOpSymbol(action.op)} ${action.right}"

        is Action.Expression -> "運算式：${redactText(action.text, 40)}"

        is Action.AskInput -> "詢問輸入：${redactText(action.prompt)}"
        is Action.ChooseMenu -> "選單選擇：${redactText(action.prompt)}"

        is Action.IfBegin -> "如果 ${describeCondition(action.condition)}"
        is Action.ElseIf -> "否則如果 ${describeCondition(action.condition)}"
        is Action.Else -> "否則"
        is Action.EndIf -> "結束如果"
        is Action.WhileBegin -> "一直重複…當 ${describeCondition(action.condition)}"
        is Action.EndWhile -> "結束重複"
        is Action.RepeatBegin -> "重複 ${numLabel(action.countExpr, action.count)} 次"
        is Action.EndRepeat -> "結束重複 N 次"
        is Action.RunRoutine -> "執行程序：${action.routineName.ifBlank { "(未選)" }}"
    }

    /** 判斷式的簡短描述（給紀錄／積木用），例如「電量 > 20」 */
    private fun describeCondition(c: Condition): String =
        if (c.op.usesRightOperand) {
            "${c.left.ifBlank { "(空)" }} ${compareOpSymbol(c.op)} ${c.right}"
        } else {
            "${c.left.ifBlank { "(空)" }} ${compareOpSymbol(c.op)}"
        }

    /** 算術運算子的符號（給紀錄／積木用） */
    private fun mathOpSymbol(op: MathOp): String = when (op) {
        MathOp.ADD -> "+"
        MathOp.SUBTRACT -> "−"
        MathOp.MULTIPLY -> "×"
        MathOp.DIVIDE -> "÷"
        MathOp.MODULO -> "餘"
    }

    /** 運算子的符號／簡短文字 */
    private fun compareOpSymbol(op: CompareOp): String = when (op) {
        CompareOp.EQUALS -> "="
        CompareOp.NOT_EQUALS -> "≠"
        CompareOp.GREATER -> ">"
        CompareOp.GREATER_EQUAL -> "≥"
        CompareOp.LESS -> "<"
        CompareOp.LESS_EQUAL -> "≤"
        CompareOp.CONTAINS -> "包含"
        CompareOp.NOT_CONTAINS -> "不包含"
        CompareOp.IS_EMPTY -> "為空"
        CompareOp.IS_NOT_EMPTY -> "不為空"
        CompareOp.IS_TRUE -> "為真"
        CompareOp.IS_FALSE -> "為假"
    }

    /** 數值參數的顯示：expr 非空顯示 expr（數字或 {{...}}），否則顯示原本的整數值 */
    private fun numLabel(expr: String, value: Int): String = expr.ifBlank { value.toString() }

    /**
     * RunLog 顯示用：截斷過長文字，避免剪貼簿／分享／朗讀的整段內容明文落地到 logs.json。
     * 截到約 [max] 字後補「…」；空白與前後空格先修剪。
     */
    private fun redactText(text: String, max: Int = 20): String {
        val trimmed = text.trim()
        return if (trimmed.length <= max) trimmed else trimmed.take(max) + "…"
    }

    /**
     * RunLog 顯示用的網址遮罩。
     *
     * [stripQuery] 為 true（HTTP 動作）時去掉 `?` 之後的 query 與 `#` 之後的 fragment——
     * webhook 的 token 幾乎都藏在 query，去掉後只留 scheme://host/path，仍足以辨識目標。
     * 「開啟網址」動作傳 false（保留網址本身，那本就是要開啟的目標），僅截斷過長者。
     */
    private fun redactUrl(rawUrl: String, stripQuery: Boolean, max: Int = 80): String {
        val trimmed = rawUrl.trim()
        val base = if (stripQuery) {
            trimmed.substringBefore('?').substringBefore('#')
        } else {
            trimmed
        }
        return if (base.length <= max) base else base.take(max) + "…"
    }

    fun ringerLabel(mode: RingerModeType): String = when (mode) {
        RingerModeType.NORMAL -> "正常"
        RingerModeType.VIBRATE -> "震動"
        RingerModeType.SILENT -> "靜音"
    }

    fun lensLabel(lensBack: Boolean): String = if (lensBack) "後鏡頭" else "前鏡頭"

    fun soundTypeLabel(type: String): String = when (type) {
        Action.SOUND_ALARM -> "鬧鐘聲"
        Action.SOUND_RINGTONE -> "鈴聲"
        else -> "通知音"
    }

    fun volumeStreamLabel(stream: VolumeStream): String = when (stream) {
        VolumeStream.MEDIA -> "媒體"
        VolumeStream.RING -> "鈴聲"
        VolumeStream.ALARM -> "鬧鐘"
        VolumeStream.NOTIFICATION -> "通知"
    }

    fun mediaKeyLabel(key: String): String = when (key) {
        Action.KEY_NEXT -> "下一首"
        Action.KEY_PREVIOUS -> "上一首"
        else -> "播放／暫停"
    }

    /** 是否已帶 URI scheme（RFC 3986：字母開頭，後接字母/數字/+/-/.） */
    private val URL_SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")

    /** 「開啟網址」動作禁止的危險 scheme（見 requireSafeViewScheme） */
    private val UNSAFE_VIEW_SCHEMES =
        setOf("file", "content", "javascript", "data", "intent", "android-app")

    /** Android 的 SCREEN_BRIGHTNESS 值域 */
    private const val MAX_BRIGHTNESS = 255

    private const val HTTP_TIMEOUT_MS = 10_000

    /** 互動動作（詢問輸入 / 選單選擇）等待使用者回應的上限，逾時記為失敗，不讓流程永遠卡住 */
    private const val INPUT_TIMEOUT_MS = 120_000L

    /** 取得目前位置的等待上限；室內收不到訊號時不會拖著整條流程 */
    private const val LOCATION_TIMEOUT_MS = 15_000L

    /** HTTP 回應保留為輸出時的長度上限，避免執行情境無限膨脹 */
    private const val MAX_HTTP_OUTPUT_CHARS = 10_000

    /** 剪貼簿項目的標籤（部分系統 UI 會顯示來源名稱） */
    private const val CLIP_LABEL = "Routina"

    /** 背景剪貼簿寫入的誠實註記 */
    private const val BACKGROUND_CLIPBOARD_NOTE = "背景寫入在部分裝置可能被系統忽略"

    private const val NOTIFY_ID_MIN = 1000
    private const val NOTIFY_ID_RANGE = 8000

    /**
     * 擷取結果通知的流水號 ID（起點避開 [NOTIFY_ID_MIN, NOTIFY_ID_MIN+RANGE) 與固定 ID 9001+）。
     * 遞增發放讓連拍 / 多次擷取的通知彼此不覆蓋。
     */
    private val CAPTURE_NOTIFY_SEQ = AtomicInteger(20000)
    private const val NOTIFY_ID_DND = 9001
    private const val NOTIFY_ID_OVERLAY = 9002
    private const val NOTIFY_ID_BLUETOOTH = 9003
    private const val NOTIFY_ID_WRITE_SETTINGS = 9004
    private const val NOTIFY_ID_CAMERA = 9005
    private const val NOTIFY_ID_MICROPHONE = 9006
    private const val REQUEST_DND_SETTINGS = 501
    private const val REQUEST_OVERLAY_SETTINGS = 502
    private const val REQUEST_BLUETOOTH_SETTINGS = 503
    private const val REQUEST_WRITE_SETTINGS = 504
    private const val REQUEST_CAMERA_SETTINGS = 505
    private const val REQUEST_MICROPHONE_SETTINGS = 506
}
