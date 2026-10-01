package io.github.ryojotravelemotion.jikokuhyo.data

import android.content.Context
import io.github.ryojotravelemotion.jikokuhyo.BuildConfig

/** 端末に残しておく設定。 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 画面で入れたトークンを優先し、無ければビルド時に埋め込んだものを使う。 */
    var apiKey: String
        get() = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.ODPT_API_KEY
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var radiusMeters: Int
        get() = prefs.getInt(KEY_RADIUS, 1000)
        set(value) = prefs.edit().putInt(KEY_RADIUS, value).apply()

    private companion object {
        const val KEY_API = "odpt_api_key"
        const val KEY_RADIUS = "radius_meters"
    }
}
