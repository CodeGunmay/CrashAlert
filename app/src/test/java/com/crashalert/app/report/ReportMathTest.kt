package com.crashalert.app.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportMathTest {
    @Test fun sevenDaysExcludesOlderAndFutureData() {
        val now = LocalDate.parse("2026-09-27")
        val records = listOf(
            DayRecord("2026-09-27", 14, 1, 1, 0),
            DayRecord("2026-09-21", 35, 2, 0, 1),
            DayRecord("2026-09-20", 80, 3, 2, 1),
            DayRecord("2026-09-28", 100, 4, 1, 1)
        )
        val week = ReportMath.summarize(records, 7, now)
        assertEquals(49, week.monitoringMinutes)
        assertEquals(3, week.impactChecks)
        assertEquals(1, week.cancelledChecks)
        assertEquals(1, week.cloudRequestsAccepted)
        assertEquals(129, ReportMath.summarize(records, 30, now).monitoringMinutes)
        assertTrue(week.shareText().contains("SMS delivery not verified"))
    }
}
