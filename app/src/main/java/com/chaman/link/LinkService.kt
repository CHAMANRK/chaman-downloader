package com.chaman.link

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hidden service (CONNECTOR.md section 3-4): exported, sirf com.chaman.permission.LINK (signature) wale bind kar sakte hain.
 * Koi UI nahi. Ye file har app me same copy hoti hai, app ka kaam LinkApp.kt me.
 */
class LinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg -> onMsg(msg); true })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun onMsg(msg: Message) {
        val to = msg.replyTo ?: return
        val raw = msg.data?.getString(LinkProto.KEY) ?: ""
        when (msg.what) {
            LinkProto.HANDSHAKE -> send(
                to, LinkProto.MANIFEST,
                LinkCore.manifest(LinkApp.info, packageName, LinkCore.versionName(this), LinkApp.actions)
            )
            LinkProto.PING -> send(
                to, LinkProto.PONG, JSONObject().put("version", LinkCore.versionName(this))
            )
            LinkProto.CALL -> onCall(to, raw)
        }
    }

    private fun onCall(to: Messenger, raw: String) {
        val req = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            reject(to, "", "bad_json")
            return
        }
        val callId = req.optString("callId", "")
        val name = req.optString("action", "")
        val act = LinkApp.actions.firstOrNull { it.name == name }
        if (act == null) {
            reject(to, callId, "unknown_action")
            return
        }
        val p = req.optJSONObject("params") ?: JSONObject()
        val bad = LinkCore.validateParams(act.params) { k -> p.opt(k).takeIf { it !== JSONObject.NULL } }
        if (bad != null) {
            reject(to, callId, bad)
            return
        }

        send(to, LinkProto.ACK, JSONObject().put("callId", callId).put("accepted", true))
        val cid = callId
        val call = object : LinkCall {
            override val callId: String = cid
            override fun progress(percent: Int, text: String) {
                send(
                    to, LinkProto.PROGRESS,
                    JSONObject().put("callId", cid).put("percent", percent).put("text", text)
                )
            }
        }
        scope.launch {
            val res = try {
                act.run(applicationContext, p, call)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                LinkResult.failed("${t::class.java.simpleName}: ${t.message}")
            }
            sendResult(to, callId, res)
        }
    }

    /** Rad: ACK accepted=false aur RESULT failed (dono me wahi error), taaki Nap ka call turant band ho. */
    private fun reject(to: Messenger, callId: String, error: String) {
        send(to, LinkProto.ACK, JSONObject().put("callId", callId).put("accepted", false).put("error", error))
        sendResult(to, callId, LinkResult.failed(error))
    }

    private fun sendResult(to: Messenger, callId: String, r: LinkResult) {
        val files = JSONArray()
        for (f in r.files) files.put(JSONObject().put("name", f.name).put("uri", f.uri).put("mime", f.mime))
        val o = JSONObject()
            .put("callId", callId)
            .put("status", r.status)
            .put("text", r.text)
            .put("files", files)
            .put("data", r.data)
        if (r.error != null) o.put("error", r.error)
        send(to, LinkProto.RESULT, o)
    }

    private fun send(to: Messenger, what: Int, body: JSONObject) {
        try {
            val m = Message.obtain(null, what)
            m.data = Bundle().apply { putString(LinkProto.KEY, body.toString()) }
            to.send(m)
        } catch (e: RemoteException) {
            // Nap band ho gaya / unbind: koi sunne wala nahi, chup raho
        }
    }
}
