package com.nishant.crywise

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One tap in the log: fed, fell asleep, woke up, nappy changed. */
data class LogEvent(val type: String, val time: Long)

/** One crying episode and, once the parent says so, what settled the baby. */
data class Episode(
    val start: Long,
    val durationSec: Int,
    val intensity: String,
    val worked: String?,
    val feedBucket: String,
    val awakeBucket: String,
    val hour: Int,
)

object EventType {
    const val FEED = "feed"
    const val SLEEP = "sleep"
    const val WAKE = "wake"
    const val NAPPY = "nappy"

    fun label(type: String) = when (type) {
        FEED -> "Fed"
        SLEEP -> "Fell asleep"
        WAKE -> "Woke up"
        NAPPY -> "Nappy changed"
        else -> type
    }
}

/** Everything is kept on the phone only, in app storage. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("crywise", Context.MODE_PRIVATE)

    var babyName: String
        get() = prefs.getString("name", "") ?: ""
        set(v) = prefs.edit().putString("name", v.trim()).apply()

    var birthMillis: Long
        get() = prefs.getLong("birth", 0L)
        set(v) = prefs.edit().putLong("birth", v).apply()

    fun ageWeeks(now: Long = System.currentTimeMillis()): Int? {
        val b = birthMillis
        if (b <= 0L || b > now) return null
        return ((now - b) / (7L * 24 * 3600 * 1000)).toInt()
    }

    // ---- Log events ----
    fun events(): List<LogEvent> {
        val arr = JSONArray(prefs.getString("events", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            LogEvent(o.getString("type"), o.getLong("time"))
        }.sortedByDescending { it.time }
    }

    fun addEvent(e: LogEvent) {
        val list = (events() + e).sortedByDescending { it.time }.take(800)
        saveEvents(list)
    }

    fun removeEvent(e: LogEvent) = saveEvents(events().filterNot { it == e })

    private fun saveEvents(list: List<LogEvent>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("type", it.type).put("time", it.time)) }
        prefs.edit().putString("events", arr.toString()).apply()
    }

    fun lastOf(type: String): LogEvent? = events().firstOrNull { it.type == type }

    // ---- Episodes ----
    fun episodes(): List<Episode> {
        val arr = JSONArray(prefs.getString("episodes", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Episode(
                start = o.getLong("start"),
                durationSec = o.optInt("dur", 0),
                intensity = o.optString("int", ""),
                worked = if (o.has("worked")) o.getString("worked") else null,
                feedBucket = o.optString("fb", "?"),
                awakeBucket = o.optString("ab", "?"),
                hour = o.optInt("hour", 0),
            )
        }.sortedByDescending { it.start }
    }

    fun saveEpisode(e: Episode) {
        val list = (episodes().filterNot { it.start == e.start } + e)
            .sortedByDescending { it.start }.take(500)
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
                .put("start", it.start).put("dur", it.durationSec).put("int", it.intensity)
                .put("fb", it.feedBucket).put("ab", it.awakeBucket).put("hour", it.hour)
            if (it.worked != null) o.put("worked", it.worked)
            arr.put(o)
        }
        prefs.edit().putString("episodes", arr.toString()).apply()
    }

    fun deleteEpisode(start: Long) {
        val keep = episodes().filterNot { it.start == start }
        prefs.edit().putString("episodes", "[]").apply()
        keep.forEach { saveEpisode(it) }
    }
}
