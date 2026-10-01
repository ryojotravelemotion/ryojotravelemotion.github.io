package io.github.ryojotravelemotion.jikokuhyo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class OdptException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NO_KEY, BAD_KEY, NETWORK, SERVER }
}

/**
 * 公共交通オープンデータセンター（ODPT）の API を呼ぶ。
 * https://developer.odpt.org/ で登録すると、無料でアクセストークンがもらえる。
 *
 * 時刻表はめったに変わらないので、応答をファイルに置いておき、
 * 電波が無いときは古いものでも使う。
 */
class OdptClient(
    private val cacheDir: File,
    private val apiKey: () -> String,
) {
    suspend fun stationsNear(lat: Double, lon: Double, radiusMeters: Int): List<Station> =
        OdptParser.stations(
            get(
                "places/odpt:Station",
                listOf("lat" to lat.toString(), "lon" to lon.toString(), "radius" to radiusMeters.toString()),
                maxAge = 0,
            ),
        )

    suspend fun searchStations(title: String): List<Station> =
        OdptParser.stations(get("odpt:Station", listOf("dc:title" to title), maxAge = DAY))

    suspend fun stationTimetables(stationId: String): List<StationTimetable> =
        OdptParser.stationTimetables(
            get("odpt:StationTimetable", listOf("odpt:station" to stationId), maxAge = 3 * DAY),
        )

    suspend fun railways(ids: Collection<String>): Map<String, RailwayInfo> =
        byIds("odpt:Railway", ids) { OdptParser.railways(it) }

    suspend fun stationTitles(ids: Collection<String>): Map<String, String> =
        byIds("odpt:Station", ids) { OdptParser.titles(it, "odpt:stationTitle") }

    suspend fun railDirectionTitles(ids: Collection<String>): Map<String, String> =
        byIds("odpt:RailDirection", ids) { OdptParser.titles(it, "odpt:railDirectionTitle") }

    suspend fun trainTypeTitles(ids: Collection<String>): Map<String, String> =
        byIds("odpt:TrainType", ids) { OdptParser.titles(it, "odpt:trainTypeTitle") }

    /** owl:sameAs はカンマ区切りで複数指定できる。長くなりすぎないよう、少しずつ聞く。 */
    private suspend fun <T> byIds(
        type: String,
        ids: Collection<String>,
        parse: (String) -> Map<String, T>,
    ): Map<String, T> {
        val result = mutableMapOf<String, T>()
        for (chunk in ids.distinct().sorted().chunked(20)) {
            result += parse(get(type, listOf("owl:sameAs" to chunk.joinToString(",")), maxAge = 30 * DAY))
        }
        return result
    }

    private suspend fun get(
        path: String,
        params: List<Pair<String, String>>,
        maxAge: Long,
    ): String = withContext(Dispatchers.IO) {
        val key = apiKey().trim()
        if (key.isEmpty()) {
            throw OdptException(OdptException.Kind.NO_KEY, "ODPT のアクセストークンが設定されていません")
        }
        val query = params.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
        val cacheFile = File(cacheDir, "odpt-${sha1("$path?$query")}.json")
        val now = System.currentTimeMillis()
        if (maxAge > 0 && cacheFile.exists() && now - cacheFile.lastModified() < maxAge) {
            return@withContext cacheFile.readText()
        }

        val url = URL("$BASE_URL$path?$query&acl:consumerKey=${encode(key)}")
        val conn = try {
            url.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext staleOrThrow(cacheFile, e)
        }
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            when {
                code == 401 || code == 403 -> throw OdptException(
                    OdptException.Kind.BAD_KEY,
                    "アクセストークンが受け付けられませんでした（HTTP $code）",
                )
                code !in 200..299 -> throw OdptException(
                    OdptException.Kind.SERVER,
                    "ODPT のサーバーがエラーを返しました（HTTP $code）",
                )
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            writeCache(cacheFile, body)
            body
        } catch (e: IOException) {
            staleOrThrow(cacheFile, e)
        } finally {
            conn.disconnect()
        }
    }

    private fun staleOrThrow(cacheFile: File, cause: IOException): String {
        if (cacheFile.exists()) return cacheFile.readText()
        throw OdptException(
            OdptException.Kind.NETWORK,
            "通信できませんでした（${cause.message ?: cause.javaClass.simpleName}）",
        )
    }

    private fun writeCache(file: File, body: String) {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(body)
            tmp.renameTo(file)
        } catch (_: IOException) {
            // 置いておけなくても、表示には差し支えない
        }
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val BASE_URL = "https://api.odpt.org/api/v4/"
        private val DAY = TimeUnit.DAYS.toMillis(1)
    }
}
