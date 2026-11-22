package widm.teleop.app

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject

// Emulator: 10.0.2.2 is the computer running the emulator.
// 8766 is the S4 server, reached through an SSH tunnel on that computer.
const val DEFAULT_SERVER_URL = "ws://10.0.2.2:8766"
private const val TAG = "TeleopLink"
private const val TICK_MS = 500L              // send `test` twice a second
private const val READY_TIMEOUT_MS = 5_000L   // welcome + first state within 5 s
private const val SILENCE_TIMEOUT_MS = 3_000L // 3 s of silence = link lost
private const val KEEPALIVE_MS = 133L         // server ends a hold 750 ms after the last keepalive
private const val RESET_RETRY_MS = 300L       // one retry after `busy`
private const val RESET_TIMEOUT_MS = 10_000L  // give up waiting for the reset's answer
private const val RESET_FAILED_SHOW_MS = 3_000L // how long the top bar says "Not reset"

enum class LinkStatus { Disconnected, Connecting, Connected }

/** 方向鍵的名字："z" + 1 → "z+"。TeleopLink 和畫面用同一個規則。 */
fun jogKeyOf(axis: String, dir: Int) = axis + if (dir > 0) "+" else "-"

/** 夾爪鍵的名字："open" → "gripper-open"。 */
fun gripperKeyOf(action: String) = "gripper-$action"

/** Talks to the teleop server over one WebSocket. All work runs on the main thread. */
class TeleopLink {
    var status by mutableStateOf(LinkStatus.Disconnected)
        private set
    var latencyMs by mutableStateOf<Long?>(null)   // last round trip of `test`
        private set
    var stateAgeMs by mutableStateOf<Long?>(null)  // time since the last `state`
        private set

    var serverEstop by mutableStateOf(false)   // the server's own E-stop latch, from `state`
        private set

    var serverEstopCount by mutableIntStateOf(0)   // +1 each time the server's E-stop turns on
        private set

    var releasing by mutableStateOf(false)               // estop_reset sent, waiting for its ack
        private set
    var releaseRefused by mutableStateOf<String?>(null)  // reason of the last refused release
        private set
    private var releaseSeq = 0L
    private var onReleased: (() -> Unit)? = null

    private val client = OkHttpClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var seq = 0L
    private var gotWelcome = false
    private var connectStartedAt = 0L  
    private var lastHeardAt = 0L       
    private var lastStateAt = 0L

    var holding by mutableStateOf(false)
        private set
    var holdKey by mutableStateOf<String?>(null)   // 正在動的是哪一顆，例如 "z+"；沒在動就是 null
        private set
    private var holdStartSeq = 0L

    private var stopType = "jog_stop"   // 夾爪是 "gripper_jog_stop"

    var resetting by mutableStateOf(false)   // reset_scene sent, answer not back yet
        private set
    var resetFailed by mutableStateOf(false)  // the last reset did not happen; shown for 3 s
        private set

    private val clearResetFailed = Runnable { resetFailed = false }
    private var resetSeq = 0L
    private var resetRetried = false

    private val resetRetry = Runnable { if (resetting) sendReset() }
    private val resetTimeout = Runnable { if (resetting) failReset("no answer within 10 s") }

    private val keepalive = object : Runnable {
        override fun run() {
            if (!holding) return
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
        stopHold()
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

    /** Start a hold: send [startType] with [payload], keep it alive, end it later with [stop]. */
    private fun beginHold(key: String, startType: String, payload: JSONObject, stop: String) {
        if (status != LinkStatus.Connected || holding || resetting) return
        holding = true
        holdKey = key
        stopType = stop
        send(startType, payload)
        holdStartSeq = seq
        mainHandler.postDelayed(keepalive, KEEPALIVE_MS)
    }

    /** Press: start moving along [axis] ("x", "y", "z") in direction [dir] (+1 or -1). */
    fun jogStart(axis: String, dir: Int) = beginHold(
        key = jogKeyOf(axis, dir),
        startType = "jog_start",
        payload = JSONObject().put("axis", axis).put("dir", dir).put("step", "fine"),
        stop = "jog_stop"
    )

    /** Release: stop the running hold (jog or gripper). */
    fun stopHold() {
        if (!holding) return
        endHold()
        send(stopType)
    }

    /** 放開某一顆鍵：只有它就是正在動的那顆，才停。 */
    fun jogRelease(axis: String, dir: Int) {
        if (holdKey == jogKeyOf(axis, dir)) stopHold()
    }

    /** Press Open/Close: move the jaws until release. [action] is "open" or "close". */
    fun gripperStart(action: String) = beginHold(
        key = gripperKeyOf(action),
        startType = "gripper_jog_start",
        payload = JSONObject().put("action", action).put("step", "coarse"),
        stop = "gripper_jog_stop"
    )

    /** 放開夾爪鍵：只有它就是正在動的那顆，才停。 */
    fun gripperRelease(action: String) {
        if (holdKey == gripperKeyOf(action)) stopHold()
    }

    /** STOP: every press sends `estop`, even when already stopped. The server ends any running hold (jog or gripper) itself. */
    fun estop() {
        endRelease()
        releaseRefused = null
        endHold()
        endReset()
        send("estop")
    }

    /** Hold-to-release finished: ask the server to release the stop. The screen unlocks only when the server says ok. */
    fun estopReset(onReleased: () -> Unit) {
        if (status != LinkStatus.Connected || releasing) return
        releaseRefused = null
        send("estop_reset")
        releaseSeq = seq
        releasing = true
        this.onReleased = onReleased
    }

    private fun endRelease() {
        releasing = false
        onReleased = null
    }

    /** Top band held 2 s: put the scene back as it started. */
    fun resetScene() {
        if (status != LinkStatus.Connected || holding || resetting) return
        resetFailed = false
        mainHandler.removeCallbacks(clearResetFailed)
        resetting = true
        resetRetried = false
        sendReset()
        mainHandler.postDelayed(resetTimeout, RESET_TIMEOUT_MS)
    }

    private fun sendReset() {
        send("reset_scene")
        resetSeq = seq
    }

    private fun onResetAck(ackStatus: String, reason: String) {
        if (ackStatus == "ok") {
            endReset()
            return
        }
        if (reason == "busy" && !resetRetried) {
            resetRetried = true
            mainHandler.postDelayed(resetRetry, RESET_RETRY_MS)
            return
        }
        failReset(reason)
    }

    private fun endReset() {
        resetting = false
        mainHandler.removeCallbacks(resetRetry)
        mainHandler.removeCallbacks(resetTimeout)
    }

    private fun failReset(why: String) {
        Log.w(TAG, "scene not reset: $why")
        endReset()
        resetFailed = true
        mainHandler.removeCallbacks(clearResetFailed)
        mainHandler.postDelayed(clearResetFailed, RESET_FAILED_SHOW_MS)
    }

    private fun endHold() {
        holding = false
        holdKey = null
        mainHandler.removeCallbacks(keepalive)
    }

    
    private fun onTick() {
        val now = SystemClock.elapsedRealtime()
        if (status == LinkStatus.Connecting && now - connectStartedAt > READY_TIMEOUT_MS) {
            drop("not ready within 5 s")
            return
        }
        if (status == LinkStatus.Connected && now - lastHeardAt > SILENCE_TIMEOUT_MS) {
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
                val estop = msg.optJSONObject("payload")?.optBoolean("estop") ?: false
                if (estop && !serverEstop) serverEstopCount++
                serverEstop = estop
                if (gotWelcome) status = LinkStatus.Connected
            }
            // the echo carries our own send time
            "test" -> latencyMs = System.currentTimeMillis() - msg.optLong("ts")
            // An ack names one of our messages: a hold's start, or the reset.
            "ack" -> {
                val p = msg.optJSONObject("payload") ?: return
                val ackSeq = p.optLong("seq")
                val ackStatus = p.optString("status")
                if (holding && ackSeq == holdStartSeq && ackStatus == "rejected") endHold()
                if (resetting && ackSeq == resetSeq) onResetAck(ackStatus, p.optString("reason"))
                if (releasing && ackSeq == releaseSeq) {
                    val done = onReleased
                    endRelease()
                    val reason = p.optString("reason")
                    // estop_not_active: the server was not stopped (e.g. our STOP never reached it), so unlocking is safe.
                    if (ackStatus == "ok" || reason == "estop_not_active") {
                        serverEstop = false
                        done?.invoke()
                    } else {
                        releaseRefused = reason
                    }
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
                val msg = try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    Log.w(TAG, "ignored malformed frame: $text")
                    return@post
                }
                handle(msg)
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
        endHold()
        endReset()
        endRelease()
        status = LinkStatus.Disconnected
        latencyMs = null
        stateAgeMs = null
        serverEstop = false
        releaseRefused = null
    }
}