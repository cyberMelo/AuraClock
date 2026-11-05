package com.custom.auraclock

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AlarmCalculationTest {

    @Test
    fun testNextAlarmCalculationWithin24Hours() {
        val timeStr = "07:30:00"
        val time = LocalTime.parse(timeStr.trim().take(5))
        val now = LocalDateTime.now()
        val target = LocalDateTime.of(LocalDate.now(), time)
        val trigger = if (target.isBefore(now)) target.plusDays(1) else target
        val triggerTs = trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val nowTs = System.currentTimeMillis()

        // Deve essere strettamente nel futuro
        assertTrue("Trigger deve essere nel futuro", triggerTs > nowTs)
        // Deve essere entro le prossime 24 ore e 1 minuto
        val maxFutureTs = nowTs + (24 * 3600 * 1000L) + 60000L
        assertTrue("Trigger deve essere entro 24 ore", triggerTs <= maxFutureTs)
    }
}
