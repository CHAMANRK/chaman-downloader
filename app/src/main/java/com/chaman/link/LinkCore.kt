package com.chaman.link

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/*
 * chaman-link: ChamanNap se jodne ka generic hissa (CONNECTOR.md contract v2).
 * Ye file har app me same copy hoti hai, app ka apna kuch yahan nahi. App ka sab kuch LinkApp.kt me.
 */

object LinkProto {
    const val LINK_VERSION = 1
    const val KEY = "json" // har Message ke Bundle me JSON String is key se

    const val HANDSHAKE = 1 // Nap -> App: manifest maango
    const val CALL = 2 // Nap -> App: {callId, action, params}
    const val PING = 3 // Nap -> App: zinda hai?

    const val MANIFEST = 101 // App -> Nap
    const val ACK = 102 // {callId, accepted, error?}
    const val PROGRESS = 103 // {callId, percent, text}
    const val RESULT = 104 // {callId, status, text, files, data, error?}
    const val PONG = 105 // {version}
}

/** Manifest ka param type: string | number | boolean | enum (+ values). */
class ParamSpec(val type: String, val required: Boolean = false, val values: List<String> = emptyList())

class LinkFile(val name: String, val uri: String, val mime: String)

class LinkResult(
    val status: String, // "done" | "failed"
    val text: String = "",
    val files: List<LinkFile> = emptyList(),
    val data: JSONObject = JSONObject(),
    val error: String? = null
) {
    companion object {
        fun done(text: String, files: List<LinkFile> = emptyList(), data: JSONObject = JSONObject()) =
            LinkResult("done", text, files, data)

        fun failed(error: String, text: String = error) = LinkResult("failed", text, error = error)
    }
}

/** Chalte call ko progress bhejne ka raasta. percent 0..100, -1 = pata nahi. */
interface LinkCall {
    val callId: String
    fun progress(percent: Int, text: String)
}

/** App ka ek kaam. Naya action = ek naya object, LinkApp.actions me ek entry. Manifest isi se banta hai. */
abstract class LinkAction {
    abstract val name: String
    abstract val desc: String
    open val mode: String = "background" // "background" | "open_app"
    abstract val result: String // "text" | "file" | "data"
    abstract val params: Map<String, ParamSpec>

    /** Background me chalta hai (IO). Exception aaye to engine use `failed` bana deta hai. */
    abstract suspend fun run(ctx: Context, p: JSONObject, call: LinkCall): LinkResult
}

class LinkAppInfo(
    val id: String,
    val naam: String,
    val oneLiner: String,
    val keywords: List<String>,
    val githubReleaseUrl: String
)

object LinkCore {
    /** Params ko manifest ke schema se check karo. null = theek, warna error text. `get` null de to param nahi hai. */
    fun validateParams(spec: Map<String, ParamSpec>, get: (String) -> Any?): String? {
        for ((k, s) in spec) {
            val v = get(k)
            if (v == null) {
                if (s.required) return "missing_param: $k"
                continue
            }
            val ok = when (s.type) {
                "string" -> v is String
                "number" -> v is Number
                "boolean" -> v is Boolean
                "enum" -> v is String && v in s.values
                else -> false
            }
            if (!ok) return "bad_param: $k (${s.type} chahiye)"
        }
        return null
    }

    /** Manifest (CONNECTOR.md section 5). Actions aur tools dono registry se, to code aur manifest alag nahi ho sakte. */
    fun manifest(info: LinkAppInfo, pkg: String, version: String, actions: List<LinkAction>): JSONObject {
        val acts = JSONArray()
        val tools = JSONArray()
        for (a in actions) {
            acts.put(JSONObject().put("name", a.name).put("desc", a.desc))
            val ps = JSONObject()
            for ((k, s) in a.params) {
                val o = JSONObject().put("type", s.type)
                if (s.required) o.put("required", true)
                if (s.type == "enum") o.put("values", JSONArray(s.values))
                ps.put(k, o)
            }
            tools.put(
                JSONObject().put("action", a.name).put("params", ps).put("mode", a.mode).put("result", a.result)
            )
        }
        return JSONObject()
            .put("link_version", LinkProto.LINK_VERSION)
            .put("id", info.id)
            .put("naam", info.naam)
            .put("package", pkg)
            .put("version", version)
            .put("one_liner", info.oneLiner)
            .put("keywords", JSONArray(info.keywords))
            .put("actions", acts)
            .put("tools", tools)
            .put("github_release_url", info.githubReleaseUrl)
    }

    fun versionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (t: Throwable) {
        ""
    }
}
