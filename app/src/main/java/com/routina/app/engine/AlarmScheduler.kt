package com.routina.app.engine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Routine
import com.routina.app.model.TimeMode
import com.routina.app.model.Trigger
import com.routina.app.model.geoCircle
import java.util.Calendar

/**
 * 定時觸發的排程管理。
 *
 * 採 single-shot chain：每次只排下一次鬧鐘，觸發後再排下一次，
 * 比 setRepeating 在 Doze 下更可靠，也能正確處理「星期幾」條件。
 * 日出 / 日落模式也因此天然正確：每次觸發後重算的必然是「明天的」日出日落。
 */
object AlarmScheduler {

    /** 日出日落計算所需的位置 */
    data class SunLocation(val lat: Double, val lng: Double)

    /** 沒有任何位置資訊時的日出 / 日落替代時間 */
    private const val DEFAULT_SUNRISE_HOUR = 6
    private const val DEFAULT_SUNSET_HOUR = 18

    /** ±10 分鐘的時間窗（無精確鬧鐘權限時的降級模式） */
    private const val INEXACT_WINDOW_MS = 10 * 60 * 1000L

    /** 以 triggerAt 為中心的時間窗排程：[triggerAt - 10 分, triggerAt + 10 分] */
    private fun scheduleInWindow(
        alarmManager: AlarmManager,
        triggerAt: Long,
        pendingIntent: PendingIntent
    ) {
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            triggerAt - INEXACT_WINDOW_MS,
            2 * INEXACT_WINDOW_MS,
            pendingIntent
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /** 重新排程所有啟用中的定時例行程序（先全部取消再排，避免殘留） */
    fun rescheduleAll(context: Context) {
        val repository = RoutineRepository.get(context)
        repository.routines.value.forEach { routine ->
            cancel(context, routine)
            if (routine.enabled && routine.trigger is Trigger.Time) {
                schedule(context, routine)
            }
        }
    }

    /** 為單一例行程序排下一次鬧鐘 */
    fun schedule(context: Context, routine: Routine) {
        val trigger = routine.trigger as? Trigger.Time ?: return
        if (!routine.enabled) return

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextTriggerTime(trigger, sunLocation(context)) ?: return
        val pendingIntent = buildPendingIntent(context, routine) ?: return

        try {
            if (canScheduleExact(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                // 降級：非精確時間窗排程（UI 會提示使用者前往授權）
                scheduleInWindow(alarmManager, triggerAt, pendingIntent)
            }
        } catch (t: Throwable) {
            // 極端情況（權限在排程瞬間被撤銷）→ 退回非精確排程
            runCatching { scheduleInWindow(alarmManager, triggerAt, pendingIntent) }
        }
    }

    fun cancel(context: Context, routine: Routine) = cancel(context, routine.id)

    fun cancel(context: Context, routineId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        // PendingIntent 比對依據是 action/data/type/class（不含 extras），
        // 因此取消時必須重建帶有相同 data 的 Intent。
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            routineId.hashCode(),
            baseIntent(context, routineId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun baseIntent(context: Context, routineId: String): Intent =
        Intent(context, AlarmReceiver::class.java).apply {
            putExtra(AlarmReceiver.EXTRA_ROUTINE_ID, routineId)
            // 讓不同 routine 的 PendingIntent 彼此獨立
            data = Uri.parse("routina://routine/$routineId")
        }

    private fun buildPendingIntent(context: Context, routine: Routine): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            routine.alarmRequestCode,
            baseIntent(context, routine.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * 日出日落計算用的位置。
     *
     * 定時觸發本身不帶座標，因此沿用 App 內任一「已選好地點」的區域觸發座標——
     * 使用者設過家 / 公司就等於告訴了我們大致所在地，不必為此多要一次定位權限。
     * 完全沒有任何位置時回傳 null，改用預設的 06:00 / 18:00。
     */
    fun sunLocation(context: Context): SunLocation? =
        RoutineRepository.get(context).routines.value
            .asSequence()
            .mapNotNull { it.trigger.geoCircle }
            .firstOrNull { it.isConfigured }
            ?.let { SunLocation(it.lat, it.lng) }

    /**
     * 計算下一次符合星期條件的觸發時間（毫秒）。
     *
     * daysOfWeek 使用 ISO-8601：1 = 週一 … 7 = 週日；空集合視為每天。
     * 星期條件比對的是「觸發時刻落在哪一天」（日出日落加上偏移後可能跨日）。
     */
    fun nextTriggerTime(
        trigger: Trigger.Time,
        location: SunLocation? = null,
        from: Calendar = Calendar.getInstance()
    ): Long? {
        val days = trigger.daysOfWeek.ifEmpty { Trigger.ALL_DAYS }

        // 從今天開始往後找 8 天（含今天，涵蓋整個星期循環）
        for (offset in 0..7) {
            val day = (from.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val candidate = triggerMillisOn(trigger, day, location)
            if (candidate <= from.timeInMillis) continue
            val at = (from.clone() as Calendar).apply { timeInMillis = candidate }
            if (isoDayOfWeek(at) in days) return candidate
        }
        return null
    }

    /** [day] 當天的觸發時刻（毫秒）：固定時間，或日出 / 日落加上偏移 */
    private fun triggerMillisOn(
        trigger: Trigger.Time,
        day: Calendar,
        location: SunLocation?
    ): Long = when (trigger.mode) {
        TimeMode.FIXED -> atTime(day, trigger.hour, trigger.minute)

        TimeMode.SUNRISE -> sunEventMillis(day, location, sunrise = true) +
            trigger.offsetMinutes * 60_000L

        TimeMode.SUNSET -> sunEventMillis(day, location, sunrise = false) +
            trigger.offsetMinutes * 60_000L
    }

    /**
     * [day] 當天的日出或日落時刻。無位置資訊、或該日極晝／極夜無日出日落時，
     * 退回預設的 06:00 / 18:00（UI 會顯示提示）。
     */
    private fun sunEventMillis(day: Calendar, location: SunLocation?, sunrise: Boolean): Long {
        val fallback = atTime(day, if (sunrise) DEFAULT_SUNRISE_HOUR else DEFAULT_SUNSET_HOUR, 0)
        if (location == null) return fallback
        val times = runCatching {
            SunCalc.sunTimes(day, location.lat, location.lng)
        }.getOrNull() ?: return fallback
        return (if (sunrise) times.sunriseMillis else times.sunsetMillis) ?: fallback
    }

    private fun atTime(day: Calendar, hour: Int, minute: Int): Long =
        (day.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Calendar.DAY_OF_WEEK（1=週日）轉 ISO-8601（1=週一 … 7=週日） */
    internal fun isoDayOfWeek(calendar: Calendar): Int =
        when (val day = calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 7
            else -> day - 1
        }
}
