package de.fantasyscout.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LivePlayer(
    val id: String, val name: String, val club: String, val position: String,
    val status: String, val comunioValue: Long?, val kickbaseValue: Long?,
    val fantasyScore: Int = 50, val fantasyLabel: String = "Beobachten",
    val fantasyReasons: List<String> = emptyList(),
    val comunioDelta: Long? = null, val kickbaseDelta: Long? = null,
    val marketHistory: List<MarketPoint> = emptyList(),
    val opponent: String? = null, val homeMatch: Boolean? = null,
    val opponentRank: Int? = null, val opponentStrength: Int? = null, val startingProbability: Int? = null
)

data class MarketPoint(val at: String, val comunioValue: Long?, val kickbaseValue: Long?)

data class LiveNews(
    val id: String, val playerId: String?, val source: String, val title: String,
    val summary: String, val url: String, val publishedAt: String,
    val relevance: String, val relevanceScore: Int = 0, val injury: Boolean = false
)

data class LiveAlert(
    val id: String, val playerId: String?, val playerName: String?, val type: String,
    val title: String, val source: String, val url: String
)

data class LiveSnapshot(
    val players: List<LivePlayer>, val news: List<LiveNews>, val alerts: List<LiveAlert>,
    val syncedAt: String?, val warnings: List<String>, val fromCache: Boolean = false
)

data class SyncResult(val playersUpdated: Int, val newsUpdated: Int, val syncedAt: String, val error: String? = null)

interface FantasyScoutRepository {
    suspend fun sync(): SyncResult
    fun fetchSnapshot(): LiveSnapshot
}

class HttpBackendRepository(private val baseUrl: String) : FantasyScoutRepository {
    private fun get(path: String): JSONObject {
        val url = URL(baseUrl.trimEnd('/') + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FantasyScout-Android/1.0")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally { connection.disconnect() }
    }

    override fun fetchSnapshot(): LiveSnapshot = parse(get("/v1/data"), false)

    override suspend fun sync(): SyncResult = try {
        val json = get("/v1/sync")
        SyncResult(json.optJSONArray("players")?.length() ?: 0, json.optJSONArray("news")?.length() ?: 0, json.optString("syncedAt"), null)
    } catch (e: Exception) { SyncResult(0, 0, "", e.message ?: "Netzwerkfehler") }

    private fun parse(json: JSONObject, fromCache: Boolean): LiveSnapshot {
        val players = mutableListOf<LivePlayer>()
        val pa = json.optJSONArray("players") ?: JSONArray()
        for (i in 0 until pa.length()) {
            val p = pa.optJSONObject(i) ?: continue
            val reasons = mutableListOf<String>()
            val ra = p.optJSONArray("fantasyReasons") ?: JSONArray()
            for (j in 0 until ra.length()) reasons += ra.optString(j)
            players += LivePlayer(
                p.optString("id"), p.optString("name"), p.optString("club"), p.optString("position"),
                p.optString("status", "unknown"), p.longOrNull("comunioValue"), p.longOrNull("kickbaseValue"),
                p.optInt("fantasyScore", 50), p.optString("fantasyLabel", "Beobachten"), reasons,
                p.longOrNull("comunioDelta"), p.longOrNull("kickbaseDelta"), parseHistory(p.optJSONArray("marketHistory")),
                p.optString("opponent").takeIf { it.isNotBlank() },
                if (p.has("homeMatch") && !p.isNull("homeMatch")) p.optBoolean("homeMatch") else null,
                if (p.has("opponentRank") && !p.isNull("opponentRank")) p.optInt("opponentRank") else null,
                if (p.has("opponentStrength") && !p.isNull("opponentStrength")) p.optInt("opponentStrength") else null,
                if (p.has("startingProbability") && !p.isNull("startingProbability")) p.optInt("startingProbability") else null
            )
        }
        val news = mutableListOf<LiveNews>()
        val na = json.optJSONArray("news") ?: JSONArray()
        for (i in 0 until na.length()) {
            val n = na.optJSONObject(i) ?: continue
            news += LiveNews(n.optString("id"), n.optString("playerId").takeIf { it.isNotBlank() }, n.optString("source"), n.optString("title"), n.optString("summary"), n.optString("url"), n.optString("publishedAt"), n.optString("relevance", "info"), n.optInt("relevanceScore", 0), n.optBoolean("injury", false))
        }
        val alerts = mutableListOf<LiveAlert>()
        val aa = json.optJSONArray("alerts") ?: JSONArray()
        for (i in 0 until aa.length()) {
            val a = aa.optJSONObject(i) ?: continue
            alerts += LiveAlert(a.optString("id"), a.optString("playerId").takeIf { it.isNotBlank() }, a.optString("playerName").takeIf { it.isNotBlank() }, a.optString("type"), a.optString("title"), a.optString("source"), a.optString("url"))
        }
        val warnings = mutableListOf<String>()
        val wa = json.optJSONArray("warnings") ?: JSONArray()
        for (i in 0 until wa.length()) warnings += wa.optString(i)
        return LiveSnapshot(players, news, alerts, json.optString("syncedAt").takeIf { it.isNotBlank() }, warnings, fromCache)
    }


    private fun parseHistory(array: JSONArray?): List<MarketPoint> {
        if (array == null) return emptyList()
        val result = mutableListOf<MarketPoint>()
        for (i in 0 until array.length()) {
            val x = array.optJSONObject(i) ?: continue
            result += MarketPoint(x.optString("at"), x.longOrNull("comunioValue"), x.longOrNull("kickbaseValue"))
        }
        return result
    }

    companion object {
        private const val PREFS = "fantasyscout_live"
        private const val KEY = "snapshot"
        fun saveCache(context: Context, snapshot: LiveSnapshot) {
            // Cache the normalized API response by using the JSON-compatible snapshot mapper below.
            val root = JSONObject().apply {
                put("syncedAt", snapshot.syncedAt ?: "")
                put("warnings", JSONArray(snapshot.warnings))
                put("players", JSONArray().also { a -> snapshot.players.forEach { p -> a.put(JSONObject().apply { put("id",p.id); put("name",p.name); put("club",p.club); put("position",p.position); put("status",p.status); p.comunioValue?.let { put("comunioValue",it) }; p.kickbaseValue?.let { put("kickbaseValue",it) }; put("fantasyScore",p.fantasyScore); put("fantasyLabel",p.fantasyLabel); put("fantasyReasons",JSONArray(p.fantasyReasons)); p.comunioDelta?.let { put("comunioDelta",it) }; p.kickbaseDelta?.let { put("kickbaseDelta",it) }; put("marketHistory",JSONArray().also { h -> p.marketHistory.forEach { m -> h.put(JSONObject().apply { put("at",m.at); m.comunioValue?.let { put("comunioValue",it) }; m.kickbaseValue?.let { put("kickbaseValue",it) } }) } }); p.opponent?.let { put("opponent",it) }; p.homeMatch?.let { put("homeMatch",it) }; p.opponentRank?.let { put("opponentRank",it) }; p.opponentStrength?.let { put("opponentStrength",it) }; p.startingProbability?.let { put("startingProbability",it) } }) }) } }) } })
                put("news", JSONArray().also { a -> snapshot.news.forEach { n -> a.put(JSONObject().apply { put("id",n.id); put("playerId",n.playerId ?: ""); put("source",n.source); put("title",n.title); put("summary",n.summary); put("url",n.url); put("publishedAt",n.publishedAt); put("relevance",n.relevance); put("relevanceScore",n.relevanceScore); put("injury",n.injury) }) } })
                put("alerts", JSONArray().also { a -> snapshot.alerts.forEach { x -> a.put(JSONObject().apply { put("id",x.id); put("playerId",x.playerId ?: ""); put("playerName",x.playerName ?: ""); put("type",x.type); put("title",x.title); put("source",x.source); put("url",x.url) }) } })
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, root.toString()).apply()
        }
        fun loadCache(context: Context): LiveSnapshot? = try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
            parseCached(JSONObject(raw))
        } catch (_: Exception) { null }
        private fun parseCached(json: JSONObject): LiveSnapshot {
            val tmp = HttpBackendRepository("http://127.0.0.1")
            val field = HttpBackendRepository::class.java.getDeclaredMethod("parse", JSONObject::class.java, Boolean::class.javaPrimitiveType)
            field.isAccessible = true
            return field.invoke(tmp, json, true) as LiveSnapshot
        }
    }
}

private fun JSONObject.longOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)
