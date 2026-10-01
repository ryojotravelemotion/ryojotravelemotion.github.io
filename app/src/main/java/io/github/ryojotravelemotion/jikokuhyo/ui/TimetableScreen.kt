package io.github.ryojotravelemotion.jikokuhyo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ryojotravelemotion.jikokuhyo.data.Departure
import io.github.ryojotravelemotion.jikokuhyo.data.Names
import io.github.ryojotravelemotion.jikokuhyo.data.StationTimetable
import io.github.ryojotravelemotion.jikokuhyo.domain.calendarLabel
import io.github.ryojotravelemotion.jikokuhyo.domain.DayType
import io.github.ryojotravelemotion.jikokuhyo.domain.calendarOrder
import io.github.ryojotravelemotion.jikokuhyo.domain.dayTypeOf
import io.github.ryojotravelemotion.jikokuhyo.domain.pickCalendar
import io.github.ryojotravelemotion.jikokuhyo.domain.serviceDate
import io.github.ryojotravelemotion.jikokuhyo.domain.serviceMinutes

/** 種別（急行など）を色で見分ける。いちばん本数の多い種別は色を付けない。 */
private val TYPE_COLORS = listOf(
    Color(0xFFD32F2F),
    Color(0xFF1976D2),
    Color(0xFF2E7D32),
    Color(0xFFEF6C00),
    Color(0xFF7B1FA2),
    Color(0xFF00838F),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(vm: MainViewModel, stationId: String, onBack: () -> Unit) {
    val timetables by vm.timetables.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val station = vm.station(stationId)
    val now = rememberNow()

    LaunchedEffect(stationId) { vm.loadTimetable(stationId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${station?.title ?: names.stationTitle(stationId)}駅")
                        Text(
                            names.railwayTitle(station?.railwayId),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadTimetable(stationId, force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "読み直す")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val load = timetables[stationId]) {
                null, TimetableLoad.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is TimetableLoad.Failed -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(load.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { vm.loadTimetable(stationId, force = true) }) { Text("もう一度読む") }
                }
                is TimetableLoad.Loaded ->
                    if (load.timetables.isEmpty()) {
                        Text(
                            "この駅の時刻表データは提供されていません。",
                            modifier = Modifier.padding(32.dp),
                        )
                    } else {
                        TimetableBody(load.timetables, names, serviceMinutes(now), dayTypeOf(serviceDate(now)))
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimetableBody(
    list: List<StationTimetable>,
    names: Names,
    nowMinutes: Int,
    todayType: DayType,
) {
    val directions = remember(list) { list.map { it.directionId ?: "" }.distinct().sorted() }
    val calendars = remember(list) { list.map { it.calendarId }.distinct().sortedBy { calendarOrder(it) } }
    val todayCalendar = pickCalendar(calendars, todayType)

    var directionIndex by rememberSaveable { mutableStateOf(0) }
    var chosenCalendar by rememberSaveable { mutableStateOf(todayCalendar) }
    val calendar = chosenCalendar.takeIf { it in calendars } ?: todayCalendar
    val dirIndex = directionIndex.coerceIn(directions.indices)

    val direction = directions[dirIndex]
    val timetable = list.firstOrNull { (it.directionId ?: "") == direction && it.calendarId == calendar }

    Column(Modifier.fillMaxSize()) {
        if (directions.size > 1) {
            PrimaryScrollableTabRow(selectedTabIndex = dirIndex, edgePadding = 8.dp) {
                directions.forEachIndexed { i, d ->
                    Tab(
                        selected = i == dirIndex,
                        onClick = { directionIndex = i },
                        text = { Text(names.directionLabel(d.ifEmpty { null })) },
                    )
                }
            }
        } else {
            Text(
                names.directionLabel(direction.ifEmpty { null }),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            calendars.forEach { c ->
                FilterChip(
                    selected = c == calendar,
                    onClick = { chosenCalendar = c },
                    label = { Text(if (c == todayCalendar) "${calendarLabel(c)}（今日）" else calendarLabel(c)) },
                )
            }
        }
        HorizontalDivider()
        if (timetable == null) {
            Text(
                "この方面・曜日の時刻表はありません。",
                modifier = Modifier.padding(32.dp),
            )
        } else {
            HourGrid(timetable, names, if (calendar == todayCalendar) nowMinutes else null)
        }
    }
}

/** 色や印の決め方。いちばん多いものは無印にし、ほかに色・略号を割り当てる。 */
private class Marks(departures: List<Departure>, names: Names) {
    val typeColors: Map<String, Color>
    val typeOrder: List<String>
    val destShort: Map<String, String>
    val destOrder: List<String>

    init {
        typeOrder = departures.mapNotNull { it.trainTypeId }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        typeColors = typeOrder.drop(1).mapIndexed { i, id -> id to TYPE_COLORS[i % TYPE_COLORS.size] }.toMap()

        destOrder = departures.map { it.destinationIds.joinToString(",") }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        destShort = destOrder.drop(1).associateWith { key ->
            key.split(",").filter { it.isNotEmpty() }.joinToString("") { names.stationTitle(it).take(2) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HourGrid(timetable: StationTimetable, names: Names, nowMinutes: Int?) {
    val marks = remember(timetable, names) { Marks(timetable.departures, names) }
    val hours = remember(timetable) { timetable.departures.groupBy { it.minutes / 60 }.toSortedMap().toList() }
    val next = nowMinutes?.let { now -> timetable.departures.firstOrNull { it.minutes >= now } }
    var selected by remember(timetable) { mutableStateOf<Departure?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(timetable.id) {
        val target = next ?: return@LaunchedEffect
        val index = hours.indexOfFirst { (h, _) -> h == target.minutes / 60 }
        if (index >= 0) listState.scrollToItem(index + 1) // 先頭は凡例
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            item { Legend(marks, names) }
            items(hours, key = { it.first }) { (hour, deps) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "${hour % 24}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(44.dp).padding(vertical = 8.dp),
                    )
                    FlowRow(Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp)) {
                        deps.forEach { d ->
                            MinuteCell(
                                d = d,
                                marks = marks,
                                isNext = d === next,
                                isPast = nowMinutes != null && d.minutes < nowMinutes,
                                onClick = { selected = d },
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Text(
                selected?.let { describe(it, names) } ?: "時刻を押すと、行き先や種別が出ます",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun MinuteCell(
    d: Departure,
    marks: Marks,
    isNext: Boolean,
    isPast: Boolean,
    onClick: () -> Unit,
) {
    val color = d.trainTypeId?.let { marks.typeColors[it] } ?: MaterialTheme.colorScheme.onSurface
    val dest = marks.destShort[d.destinationIds.joinToString(",")]
    Column(
        Modifier
            .widthIn(min = 40.dp)
            .alpha(if (isPast) 0.35f else 1f)
            .background(
                if (isNext) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "%02d".format(d.minutes % 60),
            color = color,
            fontSize = 18.sp,
            fontWeight = if (isNext) FontWeight.ExtraBold else FontWeight.Medium,
        )
        if (dest != null || d.isLast) {
            Text(
                listOfNotNull(dest, if (d.isLast) "終" else null).joinToString(" "),
                fontSize = 10.sp,
                lineHeight = 11.sp,
                color = if (d.isLast) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(marks: Marks, names: Names) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            marks.typeOrder.forEachIndexed { i, id ->
                val c = marks.typeColors[id] ?: MaterialTheme.colorScheme.onSurface
                Text(
                    if (i == 0) "黒：${names.trainTypeTitle(id)}" else "■ ${names.trainTypeTitle(id)}",
                    color = c,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (marks.destOrder.isNotEmpty()) {
            val first = marks.destOrder.first()
            val parts = buildList {
                add("無印：${destTitle(first, names)}")
                marks.destOrder.drop(1).forEach { key -> add("${marks.destShort[key]}：${destTitle(key, names)}") }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                parts.joinToString("　"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun destTitle(key: String, names: Names): String {
    val ids = key.split(",").filter { it.isNotEmpty() }
    return if (ids.isEmpty()) "行き先不明" else ids.joinToString("・") { names.stationTitle(it) } + "行"
}

private fun describe(d: Departure, names: Names): String = buildString {
    append(d.time)
    names.trainTypeTitle(d.trainTypeId).takeIf { it.isNotEmpty() }?.let { append("　").append(it) }
    append("　").append(destTitle(d.destinationIds.joinToString(","), names))
    d.platform?.let { append("　").append(it).append("番線") }
    if (d.isOrigin) append("　当駅始発")
    if (d.isLast) append("　終電")
    d.note?.let { append("\n").append(it) }
}
