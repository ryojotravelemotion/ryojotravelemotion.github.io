package io.github.ryojotravelemotion.jikokuhyo.domain

import io.github.ryojotravelemotion.jikokuhyo.data.Departure
import io.github.ryojotravelemotion.jikokuhyo.data.Station
import io.github.ryojotravelemotion.jikokuhyo.data.StationTimetable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** 二点間の距離（メートル）。 */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(a))
}

/** 不動産広告と同じく、80m を徒歩 1 分として数える（端数は切り上げ）。 */
fun walkingMinutes(meters: Int): Int = ((meters + 79) / 80).coerceAtLeast(1)

/** 同じ名前の駅（路線ごとに分かれている）を一つにまとめたもの。 */
data class StationGroup(
    val title: String,
    val distanceMeters: Int?,
    val stations: List<Station>,
)

/**
 * 駅を名前ごとにまとめ、近い順に並べる。
 * 同じ名前でも遠く離れた駅（別の町の同名駅）は、別のまとまりにする。
 */
fun groupStations(stations: List<Station>, lat: Double?, lon: Double?): List<StationGroup> {
    fun dist(s: Station): Int? =
        if (lat != null && lon != null && s.lat != null && s.lon != null) {
            distanceMeters(lat, lon, s.lat, s.lon).roundToInt()
        } else {
            null
        }

    val groups = mutableListOf<MutableList<Station>>()
    for (s in stations) {
        val same = groups.firstOrNull { g ->
            g.first().title == s.title && g.any { near(it, s) }
        }
        if (same != null) same += s else groups += mutableListOf(s)
    }
    return groups.map { g ->
        StationGroup(
            title = g.first().title,
            distanceMeters = g.mapNotNull { dist(it) }.minOrNull(),
            stations = g.sortedWith(compareBy({ it.operatorId }, { it.railwayId })),
        )
    }.sortedWith(compareBy(nullsLast<Int>()) { it.distanceMeters })
}

private fun near(a: Station, b: Station): Boolean {
    if (a.lat == null || a.lon == null || b.lat == null || b.lon == null) return true
    return distanceMeters(a.lat, a.lon, b.lat, b.lon) < 1_500
}

/** ある方面の、これから出る列車。 */
data class DirectionBoard(
    val timetable: StationTimetable,
    val next: List<Departure>,
)

/**
 * 駅の時刻表の中から、今日のダイヤでこれから出る列車を方面ごとに選ぶ。
 * [count] 本まで。その日の運転が終わった方面は、next が空になる。
 */
fun nextDepartures(
    timetables: List<StationTimetable>,
    nowServiceMinutes: Int,
    dayType: DayType,
    count: Int,
): List<DirectionBoard> {
    val calendar = pickCalendar(timetables.map { it.calendarId }.distinct(), dayType)
        ?: return emptyList()
    return timetables
        .filter { it.calendarId == calendar }
        .sortedBy { it.directionId ?: "" }
        .map { t ->
            DirectionBoard(t, t.departures.filter { it.minutes >= nowServiceMinutes }.take(count))
        }
}
