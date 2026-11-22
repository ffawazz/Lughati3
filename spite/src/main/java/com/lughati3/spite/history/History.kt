package com.lughati3.spite.history

import android.content.Context
import com.lughati3.spite.model.GameId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class Outcome { WIN, LOSS, PLAYED }

/**
 * One finished (or abandoned) game.
 *  - [score]: lower is better where the game defines one (moves, points) — see [GameId.scoreLabel].
 *  - [variant]: sub-mode that makes scores comparable only with each other (e.g. "Draw 3", "Hard").
 */
data class GameRecord(
    val game: GameId,
    val timeMillis: Long,
    val outcome: Outcome,
    val detail: String,
    val score: Int? = null,
    val variant: String? = null,
)

/** Per-game summary shown on the history screen. */
data class GameStats(
    val game: GameId,
    val played: Int,
    val wins: Int,
    val losses: Int,
    /** Best (lowest) winning score per variant. */
    val bestByVariant: Map<String?, Int>,
    val lastPlayed: Long?,
) {
    /** Win rate over games that had a winner/loser; null when there are none. */
    val winRate: Int? get() = (wins + losses).takeIf { it > 0 }?.let { wins * 100 / it }
}

fun List<GameRecord>.statsFor(game: GameId): GameStats {
    val mine = filter { it.game == game }
    val wins = mine.filter { it.outcome == Outcome.WIN }
    return GameStats(
        game = game,
        played = mine.size,
        wins = wins.size,
        losses = mine.count { it.outcome == Outcome.LOSS },
        bestByVariant = wins.filter { it.score != null }
            .groupBy { it.variant }
            .mapValues { (_, rs) -> rs.minOf { it.score!! } },
        lastPlayed = mine.maxOfOrNull { it.timeMillis },
    )
}

interface HistoryStore {
    fun load(): List<GameRecord>
    fun save(records: List<GameRecord>)
}

class InMemoryHistoryStore : HistoryStore {
    private var data: List<GameRecord> = emptyList()
    override fun load() = data
    override fun save(records: List<GameRecord>) { data = records }
}

/** Persists records as a JSON array in SharedPreferences. Unknown/corrupt entries are skipped. */
class PrefsHistoryStore(context: Context) : HistoryStore {
    private val prefs = context.applicationContext.getSharedPreferences("card_games_history", Context.MODE_PRIVATE)

    override fun load(): List<GameRecord> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    GameRecord(
                        game = GameId.valueOf(o.getString("game")),
                        timeMillis = o.getLong("t"),
                        outcome = Outcome.valueOf(o.getString("outcome")),
                        detail = o.optString("detail", ""),
                        score = if (o.has("score")) o.getInt("score") else null,
                        variant = if (o.has("variant")) o.getString("variant") else null,
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    override fun save(records: List<GameRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("game", r.game.name); put("t", r.timeMillis); put("outcome", r.outcome.name); put("detail", r.detail)
                r.score?.let { put("score", it) }
                r.variant?.let { put("variant", it) }
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object { const val KEY = "records" }
}

/**
 * App-wide history. Games call [record]; the history screen observes [records] (newest first).
 * Until [init] is called (e.g. in unit tests) records are kept in memory only.
 */
object History {
    private const val MAX_RECORDS = 500

    private var store: HistoryStore = InMemoryHistoryStore()
    private val _records = MutableStateFlow<List<GameRecord>>(emptyList())
    val records: StateFlow<List<GameRecord>> = _records.asStateFlow()

    fun init(store: HistoryStore) {
        this.store = store
        _records.value = store.load().sortedByDescending { it.timeMillis }
    }

    fun record(
        game: GameId, outcome: Outcome, detail: String,
        score: Int? = null, variant: String? = null, now: Long = System.currentTimeMillis(),
    ) {
        val next = (listOf(GameRecord(game, now, outcome, detail, score, variant)) + _records.value).take(MAX_RECORDS)
        _records.value = next
        store.save(next)
    }

    fun clear() {
        _records.value = emptyList()
        store.save(emptyList())
    }
}
