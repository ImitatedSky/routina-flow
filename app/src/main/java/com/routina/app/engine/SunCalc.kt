package com.routina.app.engine

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 日出 / 日落時間的本地計算（NOAA 太陽方程式）。
 *
 * 純函式、零依賴、不連網，誤差約 ±1–2 分鐘——對「日落後 30 分鐘關窗簾」這類
 * 生活自動化完全足夠，也避免了為此引入天文函式庫或呼叫線上 API。
 *
 * 參考：NOAA Solar Calculator（General Solar Position Calculations）。
 */
object SunCalc {

    /** 日出日落的太陽天頂角：90.833°（含大氣折射與太陽視半徑修正） */
    private const val ZENITH_DEG = 90.833

    private const val DEG_TO_RAD = Math.PI / 180.0
    private const val RAD_TO_DEG = 180.0 / Math.PI

    /** 極晝 / 極夜（該日無日出或日落）時為 null */
    data class SunTimes(val sunriseMillis: Long?, val sunsetMillis: Long?)

    /**
     * 計算 [day] 所在日期（以 [day] 的時區為準）的日出與日落時刻（epoch 毫秒）。
     *
     * @param latitude 緯度（北為正）
     * @param longitude 經度（東為正）
     */
    fun sunTimes(day: Calendar, latitude: Double, longitude: Double): SunTimes {
        val dayOfYear = day.get(Calendar.DAY_OF_YEAR)
        val utcMidnight = utcMidnightOf(day)

        // 分數年（fractional year）。NOAA 式為 2π/N × (dayOfYear − 1 + (hour − 12)/24)，
        // 以當日中午（hour = 12）為代表點時第二項為 0。
        val gamma = 2.0 * Math.PI / daysInYear(day) * (dayOfYear - 1)

        // 均時差（分鐘）
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )

        // 太陽赤緯（弧度）
        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) +
            0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) +
            0.00148 * sin(3 * gamma)

        val latRad = latitude * DEG_TO_RAD
        val cosHourAngle = cos(ZENITH_DEG * DEG_TO_RAD) / (cos(latRad) * cos(decl)) -
            tan(latRad) * tan(decl)

        // |cos| > 1 → 極晝或極夜，該日沒有日出 / 日落
        if (cosHourAngle > 1.0 || cosHourAngle < -1.0) return SunTimes(null, null)

        val hourAngleDeg = acos(cosHourAngle) * RAD_TO_DEG

        // NOAA：日出 / 日落的 UTC 時刻（自 UTC 午夜起算的分鐘數）
        val sunriseMinutes = 720.0 - 4.0 * (longitude + hourAngleDeg) - eqTime
        val sunsetMinutes = 720.0 - 4.0 * (longitude - hourAngleDeg) - eqTime

        return SunTimes(
            sunriseMillis = utcMidnight + Math.round(sunriseMinutes * 60_000.0),
            sunsetMillis = utcMidnight + Math.round(sunsetMinutes * 60_000.0)
        )
    }

    /** [day] 當地日期的 UTC 午夜（epoch 毫秒） */
    private fun utcMidnightOf(day: Calendar): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH))
        }
        return utc.timeInMillis
    }

    private fun daysInYear(day: Calendar): Double =
        day.getActualMaximum(Calendar.DAY_OF_YEAR).toDouble()
}
