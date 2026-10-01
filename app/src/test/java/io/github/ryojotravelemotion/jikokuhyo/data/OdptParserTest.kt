package io.github.ryojotravelemotion.jikokuhyo.data

import io.github.ryojotravelemotion.jikokuhyo.domain.DayType
import io.github.ryojotravelemotion.jikokuhyo.domain.groupStations
import io.github.ryojotravelemotion.jikokuhyo.domain.nextDepartures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OdptParserTest {

    private val stationsJson = """
        [
          {"@type":"odpt:Station","owl:sameAs":"odpt.Station:TokyoMetro.Ginza.Shibuya",
           "dc:title":"渋谷","odpt:stationTitle":{"ja":"渋谷","en":"Shibuya"},
           "odpt:railway":"odpt.Railway:TokyoMetro.Ginza","odpt:operator":"odpt.Operator:TokyoMetro",
           "odpt:stationCode":"G01","geo:lat":35.659,"geo:long":139.7016},
          {"@type":"odpt:Station","owl:sameAs":"odpt.Station:TokyoMetro.Hanzomon.Shibuya",
           "dc:title":"渋谷","odpt:railway":"odpt.Railway:TokyoMetro.Hanzomon",
           "odpt:stationCode":"Z01","geo:lat":35.6585,"geo:long":139.7013},
          {"@type":"odpt:Station","owl:sameAs":"odpt.Station:TokyoMetro.Ginza.OmoteSando",
           "odpt:stationTitle":{"en":"Omote-sando"},
           "odpt:railway":"odpt.Railway:TokyoMetro.Ginza","geo:lat":35.6653,"geo:long":139.7123}
        ]
    """.trimIndent()

    private val timetableJson = """
        [
          {"owl:sameAs":"odpt.StationTimetable:TokyoMetro.Ginza.Shibuya.TokyoMetro.Asakusa.Weekday",
           "odpt:station":"odpt.Station:TokyoMetro.Ginza.Shibuya",
           "odpt:railway":"odpt.Railway:TokyoMetro.Ginza",
           "odpt:railDirection":"odpt.RailDirection:TokyoMetro.Asakusa",
           "odpt:calendar":"odpt.Calendar:Weekday",
           "odpt:stationTimetableObject":[
             {"odpt:departureTime":"00:05","odpt:destinationStation":["odpt.Station:TokyoMetro.Ginza.Asakusa"],"odpt:isLast":true},
             {"odpt:departureTime":"05:10","odpt:destinationStation":["odpt.Station:TokyoMetro.Ginza.Asakusa"],
              "odpt:trainType":"odpt.TrainType:TokyoMetro.Local","odpt:note":{"ja":"当駅始発"}},
             {"odpt:arrivalTime":"05:20"},
             {"odpt:departureTime":"23:50","odpt:destinationStation":["odpt.Station:TokyoMetro.Ginza.Ueno"]}
           ]},
          {"owl:sameAs":"odpt.StationTimetable:TokyoMetro.Ginza.Shibuya.TokyoMetro.Asakusa.SaturdayHoliday",
           "odpt:station":"odpt.Station:TokyoMetro.Ginza.Shibuya",
           "odpt:railDirection":"odpt.RailDirection:TokyoMetro.Asakusa",
           "odpt:calendar":"odpt.Calendar:SaturdayHoliday",
           "odpt:stationTimetableObject":[{"odpt:departureTime":"06:00"}]}
        ]
    """.trimIndent()

    @Test
    fun parsesStations() {
        val stations = OdptParser.stations(stationsJson)
        assertEquals(3, stations.size)
        assertEquals("渋谷", stations[0].title)
        assertEquals("G01", stations[0].code)
        assertEquals("渋谷", stations[1].title) // stationTitle が無ければ dc:title
        assertEquals("Omote-sando", stations[2].title) // 日本語が無ければ英語
    }

    @Test
    fun groupsSameNameStations() {
        val groups = groupStations(OdptParser.stations(stationsJson), 35.6590, 139.7016)
        assertEquals(listOf("渋谷", "Omote-sando"), groups.map { it.title })
        assertEquals(2, groups[0].stations.size)
        assertEquals(0, groups[0].distanceMeters)
        assertTrue(groups[1].distanceMeters!! in 900..1300)
    }

    @Test
    fun parsesTimetableAndSortsLateNightLast() {
        val list = OdptParser.stationTimetables(timetableJson)
        assertEquals(2, list.size)
        val weekday = list[0]
        assertEquals(listOf("05:10", "23:50", "00:05"), weekday.departures.map { it.time })
        assertTrue(weekday.departures.last().isLast)
        assertEquals("当駅始発", weekday.departures.first().note)
    }

    @Test
    fun nextDeparturesUsesTodaysCalendar() {
        val list = OdptParser.stationTimetables(timetableJson)
        val weekday = nextDepartures(list, nowServiceMinutes = 23 * 60, dayType = DayType.WEEKDAY, count = 3)
        assertEquals(1, weekday.size)
        assertEquals(listOf("23:50", "00:05"), weekday[0].next.map { it.time })

        val holiday = nextDepartures(list, nowServiceMinutes = 5 * 60, dayType = DayType.HOLIDAY, count = 3)
        assertEquals(listOf("06:00"), holiday[0].next.map { it.time })
    }

    @Test
    fun namesFallBackToIds() {
        val names = Names()
        assertEquals("Asakusa方面", names.directionLabel("odpt.RailDirection:TokyoMetro.Asakusa"))
        assertEquals("各停", names.trainTypeTitle("odpt.TrainType:TokyoMetro.Local"))
        val named = Names(directions = mapOf("odpt.RailDirection:Inbound" to "上り"))
        assertEquals("上り", named.directionLabel("odpt.RailDirection:Inbound"))
    }
}
