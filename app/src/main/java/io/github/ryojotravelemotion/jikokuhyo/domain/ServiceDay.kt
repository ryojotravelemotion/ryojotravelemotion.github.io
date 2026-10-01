package io.github.ryojotravelemotion.jikokuhyo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

/**
 * 鉄道の「一日」は深夜 0 時ではなく、終電が終わる明け方に切り替わる。
 * この時刻より前は、前の日のダイヤの続きとして扱う。
 */
const val SERVICE_DAY_START_HOUR = 3

/** 時刻表の種別。駅の時刻表は、たいていこの三つのどれかで分かれている。 */
enum class DayType(val label: String) {
    WEEKDAY("平日"),
    SATURDAY("土曜"),
    HOLIDAY("休日"),
}

/** その時刻が、どの日のダイヤに属するか。 */
fun serviceDate(now: LocalDateTime): LocalDate =
    if (now.hour < SERVICE_DAY_START_HOUR) now.toLocalDate().minusDays(1) else now.toLocalDate()

/** ダイヤ上の分。深夜 0 時台は 24 時台として数え、終電が一日の最後に並ぶようにする。 */
fun serviceMinutes(now: LocalDateTime): Int {
    val h = if (now.hour < SERVICE_DAY_START_HOUR) now.hour + 24 else now.hour
    return h * 60 + now.minute
}

/**
 * 時刻表の "HH:MM" を、ダイヤ上の分にする。
 * 0〜2 時台は前日の続きなので 24 時を足す。読めなければ null。
 */
fun parseDepartureMinutes(time: String): Int? {
    val parts = time.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..29 || m !in 0..59) return null
    val hour = if (h < SERVICE_DAY_START_HOUR) h + 24 else h
    return hour * 60 + m
}

/** ダイヤ上の分を、時刻表で見慣れた "H:MM"（24 時以降は 0 時に戻す）にする。 */
fun formatMinutes(minutes: Int): String {
    val h = (minutes / 60) % 24
    val m = minutes % 60
    return "%d:%02d".format(h, m)
}

/**
 * その日にどの時刻表が使われるか。
 * 日曜・祝日に加え、多くの鉄道会社が休日ダイヤで走る年末年始（12/30〜1/3）も休日とする。
 */
fun dayTypeOf(date: LocalDate): DayType = when {
    date.dayOfWeek == DayOfWeek.SUNDAY -> DayType.HOLIDAY
    JapaneseHolidays.isHoliday(date) -> DayType.HOLIDAY
    isYearEndHoliday(date) -> DayType.HOLIDAY
    date.dayOfWeek == DayOfWeek.SATURDAY -> DayType.SATURDAY
    else -> DayType.WEEKDAY
}

private fun isYearEndHoliday(date: LocalDate): Boolean =
    (date.monthValue == 12 && date.dayOfMonth >= 30) ||
        (date.monthValue == 1 && date.dayOfMonth <= 3)

/**
 * 公共交通オープンデータ（ODPT）の暦の ID（odpt.Calendar:Weekday など）のうち、
 * その日の種別に合うものを選ぶ。合うものが無ければ、先頭のものを返す。
 */
fun pickCalendar(available: Collection<String>, dayType: DayType): String? {
    if (available.isEmpty()) return null
    val byName = available.associateBy { it.substringAfter("odpt.Calendar:") }
    val preferred = when (dayType) {
        DayType.WEEKDAY -> listOf("Weekday")
        DayType.SATURDAY -> listOf("Saturday", "SaturdayHoliday", "Holiday")
        DayType.HOLIDAY -> listOf("Holiday", "SaturdayHoliday", "Sunday")
    }
    return preferred.firstNotNullOfOrNull { byName[it] } ?: available.first()
}

/** 暦の ID を、画面に出す短い名前にする。 */
fun calendarLabel(calendarId: String): String =
    when (val name = calendarId.substringAfter("odpt.Calendar:")) {
        "Weekday" -> "平日"
        "Saturday" -> "土曜"
        "Holiday" -> "休日"
        "SaturdayHoliday" -> "土休日"
        "Sunday" -> "日曜"
        "Monday" -> "月曜"
        "Tuesday" -> "火曜"
        "Wednesday" -> "水曜"
        "Thursday" -> "木曜"
        "Friday" -> "金曜"
        else -> name.substringAfterLast('.')
    }

/** 平日 → 土曜 → 休日 の順に並べるための重み。 */
fun calendarOrder(calendarId: String): Int =
    when (calendarId.substringAfter("odpt.Calendar:")) {
        "Weekday" -> 0
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday" -> 1
        "Saturday" -> 2
        "SaturdayHoliday" -> 3
        "Holiday" -> 4
        "Sunday" -> 5
        else -> 6
    }

/**
 * 日本の祝日。振替休日と国民の休日も含む。
 * 現行の祝日法（2020 年以降）の決まりで計算する。春分・秋分の日の式は 2099 年まで使える。
 */
object JapaneseHolidays {
    private val cache = ConcurrentHashMap<Int, Set<LocalDate>>()

    fun isHoliday(date: LocalDate): Boolean = date in holidaysOf(date.year)

    fun holidaysOf(year: Int): Set<LocalDate> = cache.getOrPut(year) { compute(year) }

    private fun compute(year: Int): Set<LocalDate> {
        val days = sortedSetOf<LocalDate>()
        fun fixed(month: Int, day: Int) = days.add(LocalDate.of(year, month, day))
        fun nthMonday(month: Int, n: Int) = days.add(
            LocalDate.of(year, month, 1)
                .with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY)),
        )

        fixed(1, 1) // 元日
        nthMonday(1, 2) // 成人の日
        fixed(2, 11) // 建国記念の日
        fixed(2, 23) // 天皇誕生日
        fixed(3, vernalEquinoxDay(year)) // 春分の日
        fixed(4, 29) // 昭和の日
        fixed(5, 3) // 憲法記念日
        fixed(5, 4) // みどりの日
        fixed(5, 5) // こどもの日
        nthMonday(7, 3) // 海の日
        fixed(8, 11) // 山の日
        nthMonday(9, 3) // 敬老の日
        fixed(9, autumnalEquinoxDay(year)) // 秋分の日
        nthMonday(10, 2) // スポーツの日
        fixed(11, 3) // 文化の日
        fixed(11, 23) // 勤労感謝の日

        // 国民の休日：祝日に挟まれた平日
        for (d in days.toList()) {
            val between = d.plusDays(1)
            if (between !in days && d.plusDays(2) in days &&
                between.dayOfWeek != DayOfWeek.SUNDAY
            ) {
                days.add(between)
            }
        }

        // 振替休日：日曜の祝日のあと、最初の祝日でない日
        for (d in days.toList()) {
            if (d.dayOfWeek == DayOfWeek.SUNDAY) {
                var sub = d.plusDays(1)
                while (sub in days) sub = sub.plusDays(1)
                days.add(sub)
            }
        }
        return days
    }

    private fun equinoxBase(year: Int): Double =
        0.242194 * (year - 1980) - ((year - 1980) / 4)

    internal fun vernalEquinoxDay(year: Int): Int = (20.8431 + equinoxBase(year)).toInt()

    internal fun autumnalEquinoxDay(year: Int): Int = (23.2488 + equinoxBase(year)).toInt()
}
