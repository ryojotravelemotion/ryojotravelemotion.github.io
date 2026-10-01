package io.github.ryojotravelemotion.jikokuhyo.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ryojotravelemotion.jikokuhyo.data.Departure
import io.github.ryojotravelemotion.jikokuhyo.data.Names
import io.github.ryojotravelemotion.jikokuhyo.data.OdptException
import io.github.ryojotravelemotion.jikokuhyo.data.Station
import io.github.ryojotravelemotion.jikokuhyo.domain.StationGroup
import io.github.ryojotravelemotion.jikokuhyo.domain.dayTypeOf
import io.github.ryojotravelemotion.jikokuhyo.domain.nextDepartures
import io.github.ryojotravelemotion.jikokuhyo.domain.serviceDate
import io.github.ryojotravelemotion.jikokuhyo.domain.serviceMinutes
import io.github.ryojotravelemotion.jikokuhyo.domain.walkingMinutes
import java.time.LocalDateTime

private val RADII = listOf(500, 1000, 2000)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    vm: MainViewModel,
    onOpenStation: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val timetables by vm.timetables.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val now = rememberNow()

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) vm.refresh() else vm.onPermissionDenied()
    }
    val askPermission = {
        permission.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }

    LaunchedEffect(Unit) {
        // 初めて開いたときだけ、現在地で探しにいく
        if (state.phase == Phase.NEED_PERMISSION && state.groups.isEmpty() && state.query == null) {
            if (vm.hasLocationPermission()) vm.refresh() else askPermission()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.query == null) "近くの駅" else "「${state.query}」の駅") },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.LocationOn, contentDescription = "現在地で探し直す")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "設定")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(onSearch = vm::search)
            if (state.query == null) {
                Row(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RADII.forEach { r ->
                        FilterChip(
                            selected = state.radiusMeters == r,
                            onClick = { vm.setRadius(r) },
                            label = { Text(if (r < 1000) "${r}m" else "${r / 1000}km") },
                        )
                    }
                }
            }

            val busy = state.phase == Phase.LOCATING || state.phase == Phase.LOADING
            when {
                state.phase == Phase.NEED_PERMISSION -> Message(
                    "近くの駅を探すには、位置情報の利用を許可してください。\n駅名で探すこともできます。",
                    action = "位置情報を許可する",
                    onAction = askPermission,
                )
                state.phase == Phase.LOCATION_OFF -> Message(
                    "端末の位置情報がオフになっています。オンにしてから、もう一度お試しください。",
                    action = "もう一度探す",
                    onAction = vm::refresh,
                )
                state.phase == Phase.ERROR -> {
                    val needsKey = state.errorKind == OdptException.Kind.NO_KEY ||
                        state.errorKind == OdptException.Kind.BAD_KEY
                    Message(
                        state.error ?: "読み込めませんでした",
                        action = if (needsKey) "アクセストークンを設定する" else "もう一度読む",
                        onAction = if (needsKey) onOpenSettings else vm::refresh,
                    )
                }
                busy && state.groups.isEmpty() -> Loading(
                    if (state.phase == Phase.LOCATING) "現在地を調べています…" else "駅を探しています…",
                )
                else -> PullToRefreshBox(
                    isRefreshing = busy,
                    onRefresh = { if (state.query == null) vm.refresh() else vm.search(state.query!!) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    StationList(
                        groups = state.groups,
                        empty = if (state.query == null) "この範囲に駅が見つかりませんでした。範囲を広げてみてください。"
                        else "その名前の駅は見つかりませんでした。",
                        timetables = timetables,
                        names = names,
                        now = now,
                        onNeedTimetable = { vm.loadTimetable(it) },
                        onOpenStation = onOpenStation,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(onSearch: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("駅名で探す（例：渋谷）") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = {
                    text = ""
                    onSearch("")
                    focus.clearFocus()
                }) { Icon(Icons.Default.Clear, contentDescription = "消す") }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onSearch(text)
            focus.clearFocus()
        }),
    )
}

@Composable
private fun StationList(
    groups: List<StationGroup>,
    empty: String,
    timetables: Map<String, TimetableLoad>,
    names: Names,
    now: LocalDateTime,
    onNeedTimetable: (String) -> Unit,
    onOpenStation: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (groups.isEmpty()) {
            item { Text(empty, style = MaterialTheme.typography.bodyLarge) }
        }
        items(groups, key = { g -> g.stations.first().id }) { group ->
            StationCard(group, timetables, names, now, onNeedTimetable, onOpenStation)
        }
        item {
            Text(
                ODPT_CREDIT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StationCard(
    group: StationGroup,
    timetables: Map<String, TimetableLoad>,
    names: Names,
    now: LocalDateTime,
    onNeedTimetable: (String) -> Unit,
    onOpenStation: (String) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "${group.title}駅",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            group.distanceMeters?.let { d ->
                Text(
                    "徒歩${walkingMinutes(d)}分・${d}m",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        group.stations.forEachIndexed { i, station ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            LaunchedEffect(station.id) { onNeedTimetable(station.id) }
            LineBoard(station, timetables[station.id], names, now) { onOpenStation(station.id) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** 一つの路線の、方面ごとの次の列車。 */
@Composable
private fun LineBoard(
    station: Station,
    load: TimetableLoad?,
    names: Names,
    now: LocalDateTime,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineBadge(names.railway(station.railwayId), station.code)
            Spacer(Modifier.width(8.dp))
            Text(
                names.railwayTitle(station.railwayId),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("時刻表 ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        when (load) {
            null, TimetableLoad.Loading -> Hint("時刻表を読み込み中…")
            is TimetableLoad.Failed -> Hint(load.message, error = true)
            is TimetableLoad.Loaded -> {
                if (load.timetables.isEmpty()) {
                    Hint("この駅の時刻表データは提供されていません")
                } else {
                    val boards = nextDepartures(
                        load.timetables,
                        serviceMinutes(now),
                        dayTypeOf(serviceDate(now)),
                        count = 3,
                    )
                    boards.forEach { board ->
                        Text(
                            names.directionLabel(board.timetable.directionId),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (board.next.isEmpty()) {
                            Hint("本日の運転は終了しました")
                        } else {
                            Row(Modifier.fillMaxWidth()) {
                                board.next.forEach { d ->
                                    DepartureCell(d, names, serviceMinutes(now), Modifier.weight(1f))
                                }
                                repeat(3 - board.next.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DepartureCell(d: Departure, names: Names, nowMinutes: Int, modifier: Modifier) {
    val wait = d.minutes - nowMinutes
    Column(modifier.padding(end = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(d.time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(
                when {
                    wait <= 0 -> "まもなく"
                    wait < 60 -> "${wait}分後"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        val type = names.trainTypeTitle(d.trainTypeId)
        val dest = d.destinationIds.joinToString("・") { names.stationTitle(it) }
        Text(
            listOf(type, if (dest.isEmpty()) "" else "${dest}行").filter { it.isNotEmpty() }.joinToString(" ") +
                if (d.isLast) " 終電" else "",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Message(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Loading(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(text)
        }
    }
}
