package io.github.ryojotravelemotion.jikokuhyo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ryojotravelemotion.jikokuhyo.data.RailwayInfo
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** ODPT が使うよう求めている、データの出どころの表記。 */
const val ODPT_CREDIT =
    "本アプリケーション等が利用する公共交通データは、公共交通オープンデータセンターにおいて提供されるものです。" +
        "公共交通事業者により提供されたデータを元にしていますが、必ずしも正確・完全なものとは限りません。" +
        "本アプリケーションの表示内容について、公共交通事業者への直接の問合せは行わないでください。"

/** 今の時刻。発車までの分数が古くならないよう、ときどき進める。 */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = LocalDateTime.now()
        }
    }
    return now
}

fun parseColor(hex: String?): Color? =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }

/** 路線の色の札。路線記号（G、M など）があれば中に書く。 */
@Composable
fun LineBadge(railway: RailwayInfo?, stationCode: String?, modifier: Modifier = Modifier) {
    val bg = parseColor(railway?.color) ?: MaterialTheme.colorScheme.primary
    val fg = if (bg.luminance() > 0.5f) Color.Black else Color.White
    val label = stationCode ?: railway?.lineCode ?: ""
    Box(
        modifier = modifier
            .sizeIn(minWidth = 28.dp, minHeight = 22.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
