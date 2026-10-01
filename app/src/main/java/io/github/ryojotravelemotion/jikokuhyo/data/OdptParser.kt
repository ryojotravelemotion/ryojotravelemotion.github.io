package io.github.ryojotravelemotion.jikokuhyo.data

import io.github.ryojotravelemotion.jikokuhyo.domain.parseDepartureMinutes
import org.json.JSONArray
import org.json.JSONObject

/** ODPT API（JSON-LD）の応答を、アプリの形に読み替える。 */
object OdptParser {

    fun stations(json: String): List<Station> = objects(json).mapNotNull { o ->
        val id = o.optStringOrNull("owl:sameAs") ?: return@mapNotNull null
        Station(
            id = id,
            title = title(o, "odpt:stationTitle") ?: fallbackName(id),
            railwayId = o.optStringOrNull("odpt:railway") ?: "",
            operatorId = o.optStringOrNull("odpt:operator"),
            code = o.optStringOrNull("odpt:stationCode"),
            lat = o.optDoubleOrNull("geo:lat"),
            lon = o.optDoubleOrNull("geo:long"),
        )
    }

    fun stationTimetables(json: String): List<StationTimetable> = objects(json).mapNotNull { o ->
        val id = o.optStringOrNull("owl:sameAs") ?: return@mapNotNull null
        val station = o.optStringOrNull("odpt:station") ?: return@mapNotNull null
        val items = o.optJSONArray("odpt:stationTimetableObject") ?: JSONArray()
        val departures = (0 until items.length()).mapNotNull { i ->
            departure(items.optJSONObject(i) ?: return@mapNotNull null)
        }.sortedBy { it.minutes }
        StationTimetable(
            id = id,
            stationId = station,
            railwayId = o.optStringOrNull("odpt:railway"),
            directionId = o.optStringOrNull("odpt:railDirection"),
            calendarId = o.optStringOrNull("odpt:calendar") ?: "odpt.Calendar:Unknown",
            departures = departures,
        )
    }

    private fun departure(o: JSONObject): Departure? {
        // 終着駅では到着時刻しか無い列車もあるが、乗れないので載せない
        val time = o.optStringOrNull("odpt:departureTime") ?: return null
        val minutes = parseDepartureMinutes(time) ?: return null
        return Departure(
            time = time,
            minutes = minutes,
            destinationIds = stringList(o, "odpt:destinationStation"),
            trainTypeId = o.optStringOrNull("odpt:trainType"),
            isLast = o.optBoolean("odpt:isLast", false),
            isOrigin = o.optBoolean("odpt:isOrigin", false),
            platform = o.optStringOrNull("odpt:platformNumber"),
            note = multilingual(o.opt("odpt:note")),
        )
    }

    /** 路線・方面・種別・駅など、名前だけ欲しいものを ID → 名前 の表にする。 */
    fun titles(json: String, titleField: String): Map<String, String> =
        objects(json).mapNotNull { o ->
            val id = o.optStringOrNull("owl:sameAs") ?: return@mapNotNull null
            val name = title(o, titleField) ?: return@mapNotNull null
            id to name
        }.toMap()

    fun railways(json: String): Map<String, RailwayInfo> =
        objects(json).mapNotNull { o ->
            val id = o.optStringOrNull("owl:sameAs") ?: return@mapNotNull null
            id to RailwayInfo(
                title = title(o, "odpt:railwayTitle") ?: fallbackName(id),
                color = o.optStringOrNull("odpt:color"),
                lineCode = o.optStringOrNull("odpt:lineCode"),
            )
        }.toMap()

    private fun objects(json: String): List<JSONObject> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    /** 日本語の名前を優先し、無ければ dc:title、それも無ければ英語。 */
    private fun title(o: JSONObject, multilingualField: String): String? {
        val ml = o.optJSONObject(multilingualField)
        return ml?.optStringOrNull("ja")
            ?: o.optStringOrNull("dc:title")
            ?: ml?.optStringOrNull("en")
    }

    private fun multilingual(value: Any?): String? = when (value) {
        is String -> value.ifBlank { null }
        is JSONObject -> value.optStringOrNull("ja") ?: value.optStringOrNull("en")
        else -> null
    }

    private fun stringList(o: JSONObject, key: String): List<String> =
        when (val v = o.opt(key)) {
            is JSONArray -> (0 until v.length()).mapNotNull { v.optString(it).ifBlank { null } }
            is String -> listOfNotNull(v.ifBlank { null })
            else -> emptyList()
        }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }
}
