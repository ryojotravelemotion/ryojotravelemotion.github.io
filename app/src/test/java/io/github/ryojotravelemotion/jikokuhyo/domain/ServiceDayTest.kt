package io.github.ryojotravelemotion.jikokuhyo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ServiceDayTest {

    @Test
    fun holidays2026() {
        val expected = listOf(
            "2026-01-01", "2026-01-12", "2026-02-11", "2026-02-23", "2026-03-20",
            "2026-04-29", "2026-05-03", "2026-05-04", "2026-05-05", "2026-05-06",
            "2026-07-20", "2026-08-11", "2026-09-21", "2026-09-22", "2026-09-23",
            "2026-10-12", "2026-11-03", "2026-11-23",
        ).map(LocalDate::parse).toSet()
        assertEquals(expected, JapaneseHolidays.holidaysOf(2026))
    }

    @Test
    fun substituteHoliday() {
        // 2025-02-23（日）天皇誕生日 → 2/24 が振替休日
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2025, 2, 24)))
        // 2025-11-23（日）勤労感謝の日 → 11/24 が振替休日
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.of(2025, 11, 24)))
        assertFalse(JapaneseHolidays.isHoliday(LocalDate.of(2025, 11, 25)))
    }

    @Test
    fun equinox() {
        assertEquals(20, JapaneseHolidays.vernalEquinoxDay(2025))
        assertEquals(23, JapaneseHolidays.autumnalEquinoxDay(2025))
        assertEquals(21, JapaneseHolidays.vernalEquinoxDay(2027))
        assertEquals(23, JapaneseHolidays.autumnalEquinoxDay(2027))
    }

    @Test
    fun dayTypes() {
        assertEquals(DayType.WEEKDAY, dayTypeOf(LocalDate.of(2026, 10, 1))) // 木
        assertEquals(DayType.SATURDAY, dayTypeOf(LocalDate.of(2026, 10, 3)))
        assertEquals(DayType.HOLIDAY, dayTypeOf(LocalDate.of(2026, 10, 4))) // 日
        assertEquals(DayType.HOLIDAY, dayTypeOf(LocalDate.of(2026, 10, 12))) // スポーツの日
        assertEquals(DayType.HOLIDAY, dayTypeOf(LocalDate.of(2026, 12, 30))) // 年末
    }

    @Test
    fun lateNightBelongsToPreviousDay() {
        val t = LocalDateTime.of(2026, 10, 3, 0, 30) // 土曜 0:30 は金曜のダイヤ
        assertEquals(LocalDate.of(2026, 10, 2), serviceDate(t))
        assertEquals(24 * 60 + 30, serviceMinutes(t))
        assertEquals(5 * 60 + 1, serviceMinutes(LocalDateTime.of(2026, 10, 3, 5, 1)))
    }

    @Test
    fun parseTimes() {
        assertEquals(5 * 60 + 1, parseDepartureMinutes("05:01"))
        assertEquals(24 * 60 + 12, parseDepartureMinutes("00:12"))
        assertNull(parseDepartureMinutes("abc"))
        assertNull(parseDepartureMinutes("12:75"))
        assertEquals("0:12", formatMinutes(24 * 60 + 12))
    }

    @Test
    fun pickCalendars() {
        val weekdayHoliday = listOf("odpt.Calendar:Weekday", "odpt.Calendar:SaturdayHoliday")
        assertEquals("odpt.Calendar:Weekday", pickCalendar(weekdayHoliday, DayType.WEEKDAY))
        assertEquals("odpt.Calendar:SaturdayHoliday", pickCalendar(weekdayHoliday, DayType.SATURDAY))
        assertEquals("odpt.Calendar:SaturdayHoliday", pickCalendar(weekdayHoliday, DayType.HOLIDAY))

        val three = listOf("odpt.Calendar:Weekday", "odpt.Calendar:Saturday", "odpt.Calendar:Holiday")
        assertEquals("odpt.Calendar:Saturday", pickCalendar(three, DayType.SATURDAY))
        assertEquals("odpt.Calendar:Holiday", pickCalendar(three, DayType.HOLIDAY))
        assertNull(pickCalendar(emptyList(), DayType.WEEKDAY))
    }
}
