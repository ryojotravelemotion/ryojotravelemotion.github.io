package io.github.ryojotravelemotion.jikokuhyo.data

/** 駅。ODPT では、同じ場所の駅でも路線ごとに別の駅として持っている。 */
data class Station(
    val id: String,
    val title: String,
    val railwayId: String,
    val operatorId: String?,
    val code: String?,
    val lat: Double?,
    val lon: Double?,
)

/** 時刻表の一本の列車。 */
data class Departure(
    val time: String,
    /** ダイヤ上の分（深夜 0 時台は 24 時台として数える） */
    val minutes: Int,
    val destinationIds: List<String>,
    val trainTypeId: String?,
    val isLast: Boolean,
    val isOrigin: Boolean,
    val platform: String?,
    val note: String?,
)

/** ある駅・路線・方面・暦（平日/休日など）の時刻表。 */
data class StationTimetable(
    val id: String,
    val stationId: String,
    val railwayId: String?,
    val directionId: String?,
    val calendarId: String,
    val departures: List<Departure>,
)

/** 路線の名前と色。 */
data class RailwayInfo(
    val title: String,
    /** "#F39700" のような色。無いこともある */
    val color: String?,
    val lineCode: String?,
)

/** ODPT の ID（odpt.Station:TokyoMetro.Ginza.Shibuya など）から、名前が取れないときの仮の呼び名を作る。 */
fun fallbackName(id: String): String = id.substringAfterLast('.').substringAfterLast(':')
