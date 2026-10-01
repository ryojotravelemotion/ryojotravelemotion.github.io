package io.github.ryojotravelemotion.jikokuhyo.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 画面に出す名前の表。ID から名前が引けないときは、ID の末尾で代わりにする。
 */
data class Names(
    val railways: Map<String, RailwayInfo> = emptyMap(),
    val stations: Map<String, String> = emptyMap(),
    val directions: Map<String, String> = emptyMap(),
    val trainTypes: Map<String, String> = emptyMap(),
) {
    fun railway(id: String?): RailwayInfo? = id?.let { railways[it] }

    fun railwayTitle(id: String?): String =
        id?.let { railways[it]?.title ?: fallbackName(it) } ?: "路線不明"

    fun stationTitle(id: String): String = stations[id] ?: fallbackName(id)

    /** 「浅草方面」のような書き方にそろえる。上り・下り・内回り・外回りはそのまま。 */
    fun directionLabel(id: String?): String {
        if (id == null) return "方面不明"
        val title = directions[id] ?: fallbackName(id)
        val plain = listOf("上り", "下り", "内回り", "外回り", "方面", "行")
        return if (plain.any { title.contains(it) }) title else "${title}方面"
    }

    fun trainTypeTitle(id: String?): String {
        if (id == null) return ""
        trainTypes[id]?.let { return it }
        val name = fallbackName(id)
        return TRAIN_TYPE_FALLBACK[name] ?: name
    }

    companion object {
        private val TRAIN_TYPE_FALLBACK = mapOf(
            "Local" to "各停",
            "Rapid" to "快速",
            "SemiExpress" to "準急",
            "Express" to "急行",
            "RapidExpress" to "快速急行",
            "CommuterExpress" to "通勤急行",
            "LimitedExpress" to "特急",
            "CommuterLimitedExpress" to "通勤特急",
            "RapidLimitedExpress" to "快特",
        )
    }
}

/**
 * 駅と時刻表を取ってきて、覚えておく。
 * 名前（路線名・駅名など）は後からまとめて引き、[names] に足していく。
 */
class TimetableRepository(private val client: OdptClient) {

    private val timetableCache = ConcurrentHashMap<String, List<StationTimetable>>()
    private val _names = MutableStateFlow(Names())
    val names: StateFlow<Names> = _names.asStateFlow()

    // 一度聞いた ID は、見つからなくても聞き直さない
    private val asked = ConcurrentHashMap.newKeySet<String>()
    private val namesLock = Mutex()

    suspend fun nearby(lat: Double, lon: Double, radiusMeters: Int): List<Station> =
        client.stationsNear(lat, lon, radiusMeters).also { resolveStationNames(it) }

    suspend fun search(title: String): List<Station> =
        client.searchStations(title).also { resolveStationNames(it) }

    fun cachedTimetables(stationId: String): List<StationTimetable>? = timetableCache[stationId]

    suspend fun timetables(stationId: String, force: Boolean = false): List<StationTimetable> {
        if (!force) timetableCache[stationId]?.let { return it }
        val list = client.stationTimetables(stationId)
        timetableCache[stationId] = list
        resolveTimetableNames(list)
        return list
    }

    fun clearMemory() {
        timetableCache.clear()
        asked.clear()
    }

    private suspend fun resolveStationNames(stations: List<Station>) {
        val railways = stations.map { it.railwayId }.filter { it.isNotEmpty() }
        val known = stations.associate { it.id to it.title }
        _names.update { it.copy(stations = it.stations + known) }
        resolve(railways) { ids ->
            val found = client.railways(ids)
            _names.update { it.copy(railways = it.railways + found) }
        }
    }

    private suspend fun resolveTimetableNames(list: List<StationTimetable>) {
        val destinations = list.flatMap { t -> t.departures.flatMap { it.destinationIds } }
        val directions = list.mapNotNull { it.directionId }
        val trainTypes = list.flatMap { t -> t.departures.mapNotNull { it.trainTypeId } }
        val railways = list.mapNotNull { it.railwayId }

        resolve(destinations.filterNot { it in _names.value.stations }) { ids ->
            val found = client.stationTitles(ids)
            _names.update { it.copy(stations = it.stations + found) }
        }
        resolve(directions) { ids ->
            val found = client.railDirectionTitles(ids)
            _names.update { it.copy(directions = it.directions + found) }
        }
        resolve(trainTypes) { ids ->
            val found = client.trainTypeTitles(ids)
            _names.update { it.copy(trainTypes = it.trainTypes + found) }
        }
        resolve(railways) { ids ->
            val found = client.railways(ids)
            _names.update { it.copy(railways = it.railways + found) }
        }
    }

    /** まだ聞いていない ID だけを聞く。名前が取れなくても、時刻表の表示は続ける。 */
    private suspend fun resolve(ids: List<String>, fetch: suspend (List<String>) -> Unit) {
        val todo = namesLock.withLock {
            ids.distinct().filter { asked.add(it) }
        }
        if (todo.isEmpty()) return
        try {
            fetch(todo)
        } catch (e: CancellationException) {
            asked.removeAll(todo.toSet())
            throw e
        } catch (e: OdptException) {
            // 通信できなかっただけなら、次の機会に聞き直す
            if (e.kind == OdptException.Kind.NETWORK) asked.removeAll(todo.toSet())
        } catch (_: Exception) {
            // 読めない応答だった。名前は ID の末尾で代わりにする
        }
    }
}
