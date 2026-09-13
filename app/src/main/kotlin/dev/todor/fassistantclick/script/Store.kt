package dev.todor.fassistantclick.script

import android.content.Context
import android.util.Log
import dev.todor.fassistantclick.TAG
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Scripts as one JSON file each under `files/scripts/`. No database: the sibling repo keeps its
 * watchlist the same way, and the whole point of a sideloaded tool is that you can read its state
 * with `adb shell run-as`.
 */
object Store {
    private const val DIRECTORY = "scripts"
    private const val PREFS = "fclick"
    private const val KEY_LOADED = "loadedScriptId"

    fun all(context: Context): List<Script> =
        directory(context).listFiles { file -> file.extension == "json" }
            ?.mapNotNull { read(it) }
            ?.sortedBy { it.createdAt }
            .orEmpty()

    fun find(context: Context, id: String?): Script? {
        if (id == null) return null
        return read(File(directory(context), "$id.json"))
    }

    fun save(context: Context, script: Script) {
        File(directory(context), "${script.id}.json").writeText(toJson(script).toString())
    }

    fun delete(context: Context, id: String) {
        File(directory(context), "$id.json").delete()
        if (loadedId(context) == id) setLoadedId(context, null)
    }

    /** [copyOf] makes this a duplicate: same steps and timings, new id and name. */
    fun create(context: Context, name: String, copyOf: Script? = null): Script {
        val script = Script(
            id = newId(),
            name = name,
            steps = copyOf?.steps.orEmpty(),
            repeats = copyOf?.repeats ?: 1,
            countdownMs = copyOf?.countdownMs ?: 3_000L,
        )
        save(context, script)
        return script
    }

    fun loadedId(context: Context): String? =
        prefs(context).getString(KEY_LOADED, null)

    fun setLoadedId(context: Context, id: String?) {
        prefs(context).edit().apply {
            if (id == null) remove(KEY_LOADED) else putString(KEY_LOADED, id)
        }.apply()
    }

    fun loaded(context: Context): Script? = find(context, loadedId(context))

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun directory(context: Context) =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun newId() =
        System.currentTimeMillis().toString(36) + "-" + (0..0xFFFF).random().toString(16)

    private fun read(file: File): Script? {
        if (!file.isFile) return null
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (problem: Exception) {
            // A script we cannot parse is a script the user has lost; say so in the log rather
            // than crashing the screen that lists them.
            Log.w(TAG, "could not read ${file.name}", problem)
            null
        }
    }

    private fun toJson(script: Script) = JSONObject().apply {
        put("id", script.id)
        put("name", script.name)
        put("repeats", script.repeats)
        put("countdownMs", script.countdownMs)
        put("createdAt", script.createdAt)
        put("steps", JSONArray().apply {
            script.steps.forEach { step ->
                put(JSONObject().apply {
                    put("kind", step.kind.name)
                    put("durationMs", step.durationMs)
                    put("delayMs", step.delayMs)
                    put("fingers", step.fingers)
                    put("points", JSONArray().apply {
                        step.points.forEach { point ->
                            put(JSONObject().apply {
                                put("x", point.x.toDouble())
                                put("y", point.y.toDouble())
                            })
                        }
                    })
                })
            }
        })
    }

    private fun fromJson(json: JSONObject): Script {
        val steps = json.optJSONArray("steps") ?: JSONArray()
        return Script(
            id = json.getString("id"),
            name = json.getString("name"),
            repeats = json.optInt("repeats", 1),
            countdownMs = json.optLong("countdownMs", 3_000L),
            createdAt = json.optLong("createdAt", 0L),
            steps = (0 until steps.length()).map { index ->
                val step = steps.getJSONObject(index)
                val points = step.optJSONArray("points") ?: JSONArray()
                Step(
                    kind = kindOf(step.optString("kind")),
                    durationMs = step.optLong("durationMs", 60L),
                    delayMs = step.optLong("delayMs", 300L),
                    fingers = step.optInt("fingers", 2),
                    points = (0 until points.length()).map { at ->
                        val point = points.getJSONObject(at)
                        Pt(point.getDouble("x").toFloat(), point.getDouble("y").toFloat())
                    },
                )
            },
        )
    }

    private fun kindOf(name: String) =
        Kind.entries.firstOrNull { it.name == name } ?: Kind.TAP
}
