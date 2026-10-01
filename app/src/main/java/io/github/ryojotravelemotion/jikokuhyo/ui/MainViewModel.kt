package io.github.ryojotravelemotion.jikokuhyo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.ryojotravelemotion.jikokuhyo.data.Names
import io.github.ryojotravelemotion.jikokuhyo.data.OdptClient
import io.github.ryojotravelemotion.jikokuhyo.data.OdptException
import io.github.ryojotravelemotion.jikokuhyo.data.Settings
import io.github.ryojotravelemotion.jikokuhyo.data.Station
import io.github.ryojotravelemotion.jikokuhyo.data.StationTimetable
import io.github.ryojotravelemotion.jikokuhyo.data.TimetableRepository
import io.github.ryojotravelemotion.jikokuhyo.domain.StationGroup
import io.github.ryojotravelemotion.jikokuhyo.domain.groupStations
import io.github.ryojotravelemotion.jikokuhyo.location.LocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File

/** 時刻表の読み込み具合。 */
sealed interface TimetableLoad {
    data object Loading : TimetableLoad
    data class Loaded(val timetables: List<StationTimetable>) : TimetableLoad
    data class Failed(val message: String) : TimetableLoad
}

enum class Phase { NEED_PERMISSION, LOCATION_OFF, LOCATING, LOADING, READY, ERROR }

data class NearbyState(
    val phase: Phase = Phase.NEED_PERMISSION,
    val error: String? = null,
    val errorKind: OdptException.Kind? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val radiusMeters: Int = 1000,
    val groups: List<StationGroup> = emptyList(),
    /** 駅名で探しているときの言葉。null なら現在地のまわりを出す */
    val query: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val cacheDir = File(app.cacheDir, "odpt")
    private val repository = TimetableRepository(OdptClient(cacheDir) { settings.apiKey })
    private val location = LocationProvider(app)

    private val _state = MutableStateFlow(NearbyState(radiusMeters = settings.radiusMeters))
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    private val _timetables = MutableStateFlow<Map<String, TimetableLoad>>(emptyMap())
    val timetables: StateFlow<Map<String, TimetableLoad>> = _timetables.asStateFlow()

    val names: StateFlow<Names> = repository.names

    private val _apiKey = MutableStateFlow(settings.apiKey)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private var listJob: Job? = null
    private val timetableJobs = mutableMapOf<String, Job>()
    private val requestLimit = Semaphore(4)
    private val knownStations = mutableMapOf<String, Station>()

    /** 一覧に出たことのある駅。時刻表の画面で、駅名と路線を出すのに使う。 */
    fun station(id: String): Station? = knownStations[id]

    fun hasApiKey(): Boolean = settings.apiKey.isNotBlank()

    fun hasLocationPermission(): Boolean = location.hasPermission()

    /** 現在地のまわりの駅を探し直す。 */
    fun refresh() {
        _state.update { it.copy(query = null) }
        listJob?.cancel()
        listJob = viewModelScope.launch {
            if (!location.hasPermission()) {
                _state.update { it.copy(phase = Phase.NEED_PERMISSION) }
                return@launch
            }
            if (!location.isEnabled()) {
                _state.update { it.copy(phase = Phase.LOCATION_OFF) }
                return@launch
            }
            _state.update { it.copy(phase = Phase.LOCATING, error = null) }
            val here = location.current()
            if (here == null) {
                _state.update {
                    it.copy(phase = Phase.ERROR, error = "現在地がわかりませんでした。しばらくしてからもう一度お試しください。")
                }
                return@launch
            }
            _state.update { it.copy(lat = here.latitude, lon = here.longitude) }
            loadStations { repository.nearby(here.latitude, here.longitude, _state.value.radiusMeters) }
        }
    }

    fun setRadius(meters: Int) {
        settings.radiusMeters = meters
        _state.update { it.copy(radiusMeters = meters) }
        refresh()
    }

    fun onPermissionDenied() {
        _state.update { it.copy(phase = Phase.NEED_PERMISSION) }
    }

    /** 駅名で探す。空なら現在地のまわりに戻る。 */
    fun search(text: String) {
        val q = text.trim().removeSuffix("駅")
        if (q.isEmpty()) {
            refresh()
            return
        }
        _state.update { it.copy(query = q) }
        listJob?.cancel()
        listJob = viewModelScope.launch {
            loadStations { repository.search(q) }
        }
    }

    private suspend fun loadStations(fetch: suspend () -> List<Station>) {
        _state.update { it.copy(phase = Phase.LOADING, error = null, errorKind = null) }
        try {
            val stations = fetch()
            val s = _state.value
            val groups = groupStations(stations, s.lat, s.lon)
            stations.forEach { knownStations[it.id] = it }
            // 時刻表は、一覧で駅が画面に出たときに読みにいく
            _state.update { it.copy(phase = Phase.READY, groups = groups) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OdptException) {
            _state.update { it.copy(phase = Phase.ERROR, error = e.message, errorKind = e.kind) }
        } catch (e: Exception) {
            _state.update { it.copy(phase = Phase.ERROR, error = "読み込めませんでした（${e.message}）") }
        }
    }

    /** 駅の時刻表を読む。読み込み中・読み込み済みなら何もしない（[force] で読み直す）。 */
    fun loadTimetable(stationId: String, force: Boolean = false) {
        val current = _timetables.value[stationId]
        if (!force && (current is TimetableLoad.Loaded || timetableJobs[stationId]?.isActive == true)) return
        _timetables.update { it + (stationId to TimetableLoad.Loading) }
        timetableJobs[stationId] = viewModelScope.launch {
            val result = try {
                requestLimit.withPermit {
                    TimetableLoad.Loaded(repository.timetables(stationId, force))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                TimetableLoad.Failed(e.message ?: "読み込めませんでした")
            }
            _timetables.update { it + (stationId to result) }
        }
    }

    fun saveApiKey(key: String) {
        settings.apiKey = key
        _apiKey.value = settings.apiKey
        // 失敗した読み込みを、新しいトークンでやり直せるようにする
        _timetables.update { map -> map.filterValues { it is TimetableLoad.Loaded } }
        repository.clearMemory()
    }

    fun clearCache() {
        cacheDir.deleteRecursively()
        repository.clearMemory()
        _timetables.value = emptyMap()
    }
}
