package com.crashalert.app.report

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class DayRecord(
    val date: String,
    val monitoringMinutes: Int = 0,
    val impactChecks: Int = 0,
    val cancelledChecks: Int = 0,
    val cloudRequestsAccepted: Int = 0
)

data class MonitoringReport(
    val days: Int,
    val from: LocalDate,
    val through: LocalDate,
    val monitoringMinutes: Int,
    val impactChecks: Int,
    val cancelledChecks: Int,
    val cloudRequestsAccepted: Int
) {
    fun shareText(): String = "CrashAlert · last $days days ($from to $through)\n" +
        "Recorded monitoring: $monitoringMinutes whole minutes\n" +
        "Possible-impact checks: $impactChecks\n" +
        "Checks cancelled: $cancelledChecks\n" +
        "Cloud requests accepted: $cloudRequestsAccepted (SMS delivery not verified)\n" +
        "Counts reflect this device's recorded activity only. Missing/offline service time is not included."
}

object ReportMath {
    fun summarize(records: List<DayRecord>, days: Int, today: LocalDate): MonitoringReport {
        require(days in 1..365)
        val from = today.minusDays(days.toLong() - 1)
        val subset = records.filter {
            val day = runCatching { LocalDate.parse(it.date) }.getOrNull()
            day != null && !day.isBefore(from) && !day.isAfter(today)
        }
        return MonitoringReport(days, from, today,
            subset.sumOf { it.monitoringMinutes }, subset.sumOf { it.impactChecks },
            subset.sumOf { it.cancelledChecks }, subset.sumOf { it.cloudRequestsAccepted })
    }
}

/** Whole-minute heartbeats avoid claiming time after Android stops the service. */
class MonitoringReportStore(context: Context) {
    private val prefs = context.getSharedPreferences("monitoring_history", Context.MODE_PRIVATE)
    private var lastHeartbeat = 0L

    @Synchronized fun resetSession() { lastHeartbeat = 0L }

    @Synchronized fun heartbeat(elapsedMillis: Long, today: LocalDate = LocalDate.now()): Boolean {
        if (lastHeartbeat == 0L || elapsedMillis < lastHeartbeat) {
            lastHeartbeat = elapsedMillis
            return false
        }
        if (elapsedMillis - lastHeartbeat >= 60_000L) {
            lastHeartbeat = elapsedMillis
            update(today) { it.copy(monitoringMinutes = it.monitoringMinutes + 1) }
            return true
        }
        return false
    }

    @Synchronized fun impact(today: LocalDate = LocalDate.now()) = update(today) { it.copy(impactChecks = it.impactChecks + 1) }
    @Synchronized fun cancelled(today: LocalDate = LocalDate.now()) = update(today) { it.copy(cancelledChecks = it.cancelledChecks + 1) }
    @Synchronized fun cloudAccepted(today: LocalDate = LocalDate.now()) = update(today) { it.copy(cloudRequestsAccepted = it.cloudRequestsAccepted + 1) }

    @Synchronized fun report(days: Int, today: LocalDate = LocalDate.now()): MonitoringReport = ReportMath.summarize(read(), days, today)

    private fun update(today: LocalDate, change: (DayRecord) -> DayRecord) {
        val records = read().toMutableList()
        val index = records.indexOfFirst { it.date == today.toString() }
        if (index >= 0) records[index] = change(records[index]) else records.add(change(DayRecord(today.toString())))
        val keepAfter = today.minusDays(45)
        val array = JSONArray()
        records.filter { runCatching { LocalDate.parse(it.date) >= keepAfter }.getOrDefault(false) }
            .forEach { array.put(JSONObject().put("date", it.date).put("minutes", it.monitoringMinutes)
                .put("impacts", it.impactChecks).put("cancelled", it.cancelledChecks)
                .put("cloud", it.cloudRequestsAccepted)) }
        prefs.edit().putString("days", array.toString()).apply()
    }

    private fun read(): List<DayRecord> = try {
        val arr = JSONArray(prefs.getString("days", "[]"))
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let {
            DayRecord(it.getString("date"), it.optInt("minutes"), it.optInt("impacts"),
                it.optInt("cancelled"), it.optInt("cloud"))
        } }
    } catch (_: Exception) { emptyList() }
}
