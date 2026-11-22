package widm.teleop.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

// Emulator: 10.0.2.2 is the computer running the emulator.
// 8766 is the S4 server, reached through an SSH tunnel on that computer.
const val DEFAULT_SERVER_URL = "ws://10.0.2.2:8766"
private const val TAG = "TeleopLink"
private const val TICK_MS = 500L              // send `test` twice a second
private const val READY_TIMEOUT_MS = 5_000L   // welcome + first state within 5 s
private const val SILENCE_TIMEOUT_MS = 3_000L // 3 s of silence = link lost
private const val KEEPALIVE_MS = 133L         // server ends a jog 750 ms after the last keepalive

enum class LinkStatus { Disconnected, Connecting, Connected }

/** Talks to the teleop server over one WebSocket. All work runs on the main thread. */
class TeleopLink {
    var status by mutableStateOf(LinkStatus.Disconnected)
        private set
    var latencyMs by mutableStateOf<Long?>(null)   // last round trip of `test`
        private set
    var stateAgeMs by mutableStateOf<Long?>(null)  // time since the last `state`
        private set

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var seq = 0L
    private var gotWelcome = false
    private var connectStartedAt = 0L  
    private var lastHeardAt = 0L       
    private var lastStateAt = 0L

    var jogging by mutableStateOf(false)
        private set
    private var jogStartSeq = 0L

    private val keepalive = object : Runnable {
        override fun run() {
            if (!jogging) return
            send("jog_keepalive")
            mainHandler.postDelayed(this, KEEPALIVE_MS)
        }
    }

    // runs every TICK_MS while a socket is open
    private val tick = object : Runnable {
        override fun run() {
            onTick()
            if (socket != null) mainHandler.postDelayed(this, TICK_MS)
        }
    }

    fun connect(url: String) {
        close()
        seq = 0
        gotWelcome = false
        status = LinkStatus.Connecting
        connectStartedAt = SystemClock.elapsedRealtime()  
        lastHeardAt = connectStartedAt                    
        lastStateAt = 0L                                  
        socket = client.newWebSocket(Request.Builder().url(url).build(), Listener())
        mainHandler.postDelayed(tick, TICK_MS)            
    }

    fun close() {
        jogStop()
        mainHandler.removeCallbacks(tick)
        socket?.close(1000, "bye")
        socket = null
        resetLinkState()
    }

    fun send(type: String, payload: JSONObject = JSONObject()) {
        val ws = socket ?: return
        seq += 1
        val msg = JSONObject()
            .put("v", 1)
            .put("type", type)
            .put("ts", System.currentTimeMillis())
            .put("seq", seq)
            .put("payload", payload)
        ws.send(msg.toString())
        if (type != "test" && type != "jog_keepalive") Log.d(TAG, "-> $msg")
    }

    /** Press: start moving along [axis] ("x", "y", "z") in direction [dir] (+1 or -1). */
    fun jogStart(axis: String, dir: Int) {
        if (status != LinkStatus.Connected || jogging) return
        jogging = true
        send("jog_start", JSONObject().put("axis", axis).put("dir", dir).put("step", "fine"))
        jogStartSeq = seq
        mainHandler.postDelayed(keepalive, KEEPALIVE_MS)
    }

    /** Release: stop moving. */
    fun jogStop() {
        if (!jogging) return
        endJog()
        send("jog_stop")
    }

    /** STOP: every press sends `estop`, even when already stopped. The server ends any running jog itself. */
    fun estop() {
        endJog()
        send("estop")
    }

    /** Hold-to-release finished: ask the server to release the stop and keep the scene. */
    fun estopReset() {
        send("estop_reset")
    }

    private fun endJog() {
        jogging = false
        mainHandler.removeCallbacks(keepalive)
    }

    
    private fun onTick() {
        val now = SystemClock.elapsedRealtime()
        if (status == LinkStatus.Connecting && now - connectStartedAt > READY_TIMEOUT_MS) {
            drop("not ready within 5 s")
            return
        }
        if (now - lastHeardAt > SILENCE_TIMEOUT_MS) {
            drop("no message for 3 s")
            return
        }
        if (status == LinkStatus.Connected) send("test")
        stateAgeMs = if (lastStateAt == 0L) null else now - lastStateAt
    }

    private fun handle(msg: JSONObject) {
        lastHeardAt = SystemClock.elapsedRealtime()  
        val type = msg.optString("type")
        if (type != "state" && type != "test") Log.d(TAG, "<- $msg")
        when (type) {
            "welcome" -> gotWelcome = true
            "state" -> {
                lastStateAt = lastHeardAt  
                if (gotWelcome) status = LinkStatus.Connected
            }
            // the echo carries our own send time
            "test" -> latencyMs = System.currentTimeMillis() - msg.optLong("ts")
            // The server ended our jog by itself (busy, estop, watchdog): stop keepalives.
            "ack" -> {
                val p = msg.optJSONObject("payload") ?: return
                if (jogging && p.optLong("seq") == jogStartSeq && p.optString("status") == "rejected") {
                    endJog()
                }
            }
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            mainHandler.post {
                if (socket !== webSocket) return@post
                send("hello", JSONObject().put("client", "phone").put("name", "teleop-android/0.1"))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            mainHandler.post {
                if (socket !== webSocket) return@post
                handle(JSONObject(text))
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            mainHandler.post { lost(webSocket, "closed $code") }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            mainHandler.post { lost(webSocket, "failure $t") }
        }
    }

    // we decide the link is dead and hang up
    private fun drop(why: String) {
        val ws = socket ?: return
        lost(ws, why)
        ws.cancel()
    }

    private fun lost(webSocket: WebSocket, why: String) {
        if (socket !== webSocket) return
        Log.w(TAG, "link lost: $why")
        mainHandler.removeCallbacks(tick)  
        socket = null
        resetLinkState()                   
    }

    
    private fun resetLinkState() {
        endJog()
        status = LinkStatus.Disconnected
        latencyMs = null
        stateAgeMs = null
    }
}