package widm.teleop.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign

// 顏色集中處，之後要改色只改這邊
private val StandbyBlue = Color(0xFF2563EB)
private val ConnectedGreen = Color(0xFF16A34A)
private val TextDark = Color(0xFF111827)
private val BorderGray = Color(0xFFE5E7EB)
private val CameraBg = Color(0xFF1E293B)
private val BaseStripBg = Color(0xFF0F172A)
private val PlateBg = Color(0xFFE2E8F0)
private val TextMuted = Color(0xFF64748B)
private val TextOnDark = Color(0xFFCBD5E1)
private val StopRed = Color(0xFFDC2626)
private val StopYellow = Color(0xFFFACC15)
private val PageBg = Color(0xFFF5F7FA)
private val PanelBg = Color(0xFFF8FAFC)
private val CloseGray = Color(0xFFE2E8F0)
private val EStopRed = Color(0xFFDC2626)
private val EStopPink = Color(0xFFFEF2F2)
private val EStopPinkBorder = Color(0xFFFCA5A5)
private val HoldOrange = Color(0xFFF59E0B)
private val PadText = Color(0xFF0F172A)

// 中間區域的操作模式
enum class ControlMode(val label: String) {
    Buttons("Buttons"),
    Camera("Camera")
}

// 整頁：由上到下 ① 頂部列 ② 分頁 ③ 按鈕模式／視角區 ④ 三個按鈕 ⑤ STOP
@Composable
fun ControlScreen(modifier: Modifier = Modifier) {
    var showConnection by rememberSaveable { mutableStateOf(false) }
    var eStopped by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ControlMode.Buttons) }
    val link = remember { TeleopLink() }

    DisposableEffect(link) {
        link.connect(DEFAULT_SERVER_URL)
        onDispose { link.close() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.weight(1f)) {
            // 第 1 層：原本的三樣東西
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TopBar(
                    eStopped = eStopped,
                    linkStatus = link.status,
                    resetting = link.resetting,
                    resetFailed = link.resetFailed,
                    canReset = !eStopped && link.status == LinkStatus.Connected && !link.holding && !link.resetting,
                    onReset = { link.resetScene() },
                    onConnectedClick = { if (!eStopped) showConnection = true }
                )
                if (eStopped) {
                    EStopScreen(
                        onRelease = {
                            link.estopReset()
                            eStopped = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    ModeTabs(selected = mode, onSelect = { mode = it })
                    when (mode) {
                        ControlMode.Buttons -> ButtonPad(
                            holdKey = link.holdKey,
                            onJogPress = { axis, dir -> link.jogStart(axis, dir) },
                            onJogRelease = { axis, dir -> link.jogRelease(axis, dir) },
                            onGripperPress = { link.gripperStart(it) },
                            onGripperRelease = { link.gripperRelease(it) },
                            modifier = Modifier.weight(1f)
                        )
                        ControlMode.Camera -> CameraArea(Modifier.weight(1f))
                    }
                    BottomButtons()
                }
            }

            if (showConnection) {
                // 第 2 層：變暗遮罩，點了關面板
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null      // 點了不要出現水波紋
                        ) { showConnection = false }
                )
                // 第 3 層：面板貼底
                ConnectionPanel(
                    onClose = { showConnection = false },
                    latencyMs = link.latencyMs,
                    stateAgeMs = link.stateAgeMs,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* 吃掉點擊，什麼都不做 */ }
                )
            }
        }

        StopButton(onClick = {
            showConnection = false   // 急停優先：順手關掉面板
            link.estop()
            eStopped = true
        })
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 840)
@Composable
private fun ControlScreenPreview() {
    ControlScreen()
}

// ① 頂部列：左邊 Standby，右邊 Connected（假資料，寫死）
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    eStopped: Boolean = false,
    resetting: Boolean,
    resetFailed: Boolean,
    canReset: Boolean,
    linkStatus: LinkStatus,
    onReset: () -> Unit,
    onConnectedClick: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentOnReset by rememberUpdatedState(onReset)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左半邊：長按 2 秒重置場景（右邊 Connected 點一下還是開面板）
        Row(
            modifier = Modifier
                .weight(1f)
                .pointerInput(canReset) {
                    if (!canReset) return@pointerInput
                    detectTapGestures(onPress = {
                        val job = scope.launch {
                            progress.animateTo(1f, tween(durationMillis = 2000, easing = LinearEasing))
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            currentOnReset()
                        }
                        try {
                            tryAwaitRelease()
                        } finally {
                            job.cancel()
                            scope.launch { progress.snapTo(0f) }
                        }
                    })
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (pillText, pillColor) = when {
                eStopped -> "E-stopped" to EStopRed
                resetting -> "Resetting…" to HoldOrange
                progress.value > 0f -> "Hold to reset" to StandbyBlue
                resetFailed -> "Not reset" to EStopRed
                else -> "Standby" to StandbyBlue
            }
            Text(
                text = pillText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .background(pillColor, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            if (progress.value > 0f) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.5.dp,
                    color = StandbyBlue,
                    trackColor = CloseGray
                )
            }
        }

        // 狀態點 + 連線狀態 + 箭頭
        Row(
            modifier = Modifier.clickable { onConnectedClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (dotColor, linkText) = when (linkStatus) {
                LinkStatus.Connected -> ConnectedGreen to "Connected"
                LinkStatus.Connecting -> HoldOrange to "Connecting…"
                LinkStatus.Disconnected -> TextMuted to "Disconnected"
            }
            Box(Modifier.size(8.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(linkText, color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(12.dp))
            Text("›", color = TextDark, fontSize = 18.sp)
        }
    }
}

// 只在 Android Studio 預覽用，不會出現在 app 裡
@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun TopBarPreview() {
    TopBar(
        Modifier.padding(16.dp),
        resetting = false,
        resetFailed = false,
        canReset = true,
        linkStatus = LinkStatus.Connected,
        onReset = {},
        onConnectedClick = {}
    )
}

// 分頁：切換中間區域的模式
@Composable
private fun ModeTabs(
    selected: ControlMode,
    onSelect: (ControlMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PlateBg, RoundedCornerShape(10.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ControlMode.entries.forEach { m ->
            val isSelected = m == selected
            Text(
                text = m.label,
                color = if (isSelected) TextDark else TextMuted,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) Color.White else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(m) }
                    .padding(vertical = 8.dp)
            )
        }
    }
}

// ② 視角區：深藍底，上面貼 4 樣東西
@Composable
fun CameraArea(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CameraBg, RoundedCornerShape(12.dp))
    ) {
        GripperPlate(Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
        AimMark(Modifier.align(Alignment.Center))
        BaseStrip(Modifier.align(Alignment.TopEnd).padding(top = 72.dp, end = 10.dp))
        HintCard(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

// 按住才動的方向鍵（網頁版按鈕模式的樣式）：按下送 start，放開送 stop
@Composable
private fun JogButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val contentColor = if (active) Color.White else PadText
    Column(
        modifier = modifier
            .background(if (active) StandbyBlue else Color.White, RoundedCornerShape(12.dp))
            .border(
                width = 1.5.dp,
                color = if (active) Color.Transparent else PadText.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    currentOnPress()
                    try {
                        tryAwaitRelease()
                    } finally {
                        currentOnRelease()
                    }
                })
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// 上方：夾爪狀態牌
@Composable
private fun GripperPlate(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(PlateBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 32.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Gripper: open", color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Text("Close: 1 pulse. Open: 2 pulses.", color = TextMuted, fontSize = 12.sp)
    }
}

// 正中間：十字 + Gripper aim
@Composable
private fun AimMark(modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("+", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Light)
        Text("Gripper aim", color = TextOnDark, fontSize = 13.sp)
    }
}

// 右側：Base 直條（只有左邊兩個角是圓的）
@Composable
private fun BaseStrip(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight(0.75f)
            .background(BaseStripBg, shape)
            .border(1.dp, Color(0x80FFFFFF), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.OpenWith, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(8.dp))
        Text("Base", color = Color.White, fontSize = 16.sp)
    }
}

// 下方：音量鍵提示卡（只是文字，不接音量鍵）
@Composable
private fun HintCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Hold vol+: move arm", color = Color.White, fontSize = 13.sp)
            Text("Press vol−: gripper", color = Color.White, fontSize = 13.sp)
            Text("Hold right edge: move base", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA, widthDp = 380)
@Composable
private fun CameraAreaPreview() {
    CameraArea(Modifier.padding(16.dp).height(600.dp))
}

// 按鈕模式：左邊十字、右邊 Up/Down、下排手腕和夾爪（排版參考網頁版按鈕模式）
@Composable
fun ButtonPad(
    holdKey: String?,
    onJogPress: (axis: String, dir: Int) -> Unit,
    onJogRelease: (axis: String, dir: Int) -> Unit,
    onGripperPress: (action: String) -> Unit,
    onGripperRelease: (action: String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 一顆方向鍵：axis/dir 決定按下送什麼，也決定什麼時候變藍
    @Composable
    fun Jog(label: String, icon: ImageVector, axis: String, dir: Int, modifier: Modifier) {
        JogButton(
            label = label,
            icon = icon,
            active = holdKey == jogKeyOf(axis, dir),
            onPress = { onJogPress(axis, dir) },
            onRelease = { onJogRelease(axis, dir) },
            modifier = modifier
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CameraBg, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 左：十字，3×3 格子，四個角和正中間空著
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    Jog("Forward", Icons.Rounded.KeyboardArrowUp, "y", -1, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Jog("Left", Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "x", 1, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.weight(1f))
                    Jog("Right", Icons.AutoMirrored.Rounded.KeyboardArrowRight, "x", -1, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    Jog("Back", Icons.Rounded.KeyboardArrowDown, "y", 1, Modifier.weight(1f).fillMaxHeight())
                    Spacer(Modifier.weight(1f))
                }
            }
            // 右：Up / Down
            Column(
                modifier = Modifier.width(88.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Jog("Up", Icons.Rounded.VerticalAlignTop, "z", 1, Modifier.weight(1f).fillMaxWidth())
                Jog("Down", Icons.Rounded.VerticalAlignBottom, "z", -1, Modifier.weight(1f).fillMaxWidth())
            }
        }

        // 下排：手腕先排位置，還不接 server；夾爪按住才動
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val cell = Modifier.weight(1f).fillMaxHeight()
            JogButton("Wrist ↑", Icons.Rounded.ArrowUpward, active = false, onPress = {}, onRelease = {}, modifier = cell)
            JogButton("Wrist ↓", Icons.Rounded.ArrowDownward, active = false, onPress = {}, onRelease = {}, modifier = cell)
            JogButton(
                "Open", Icons.Rounded.OpenInFull,
                active = holdKey == gripperKeyOf("open"),
                onPress = { onGripperPress("open") },
                onRelease = { onGripperRelease("open") },
                modifier = cell
            )
            JogButton(
                "Close", Icons.Rounded.CloseFullscreen,
                active = holdKey == gripperKeyOf("close"),
                onPress = { onGripperPress("close") },
                onRelease = { onGripperRelease("close") },
                modifier = cell
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA, widthDp = 380, heightDp = 560)
@Composable
private fun ButtonPadPreview() {
    ButtonPad(holdKey = "y-", onJogPress = { _, _ -> }, onJogRelease = { _, _ -> }, onGripperPress = {}, onGripperRelease = {}, modifier = Modifier.padding(16.dp))
}

// ③ 底部三個按鈕（A-13 只做外觀，按了沒反應）
@Composable
fun BottomButtons(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ActionButton(label = "Record", highlighted = true, modifier = Modifier.weight(1f)) {
            Box(Modifier.size(14.dp).border(2.dp, StandbyBlue, CircleShape))
        }
        ActionButton(label = "Reset", modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        ActionButton(label = "Settings", modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

// 三個按鈕長得差不多，寫成一個共用的函式
@Composable
private fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    icon: @Composable () -> Unit
) {
    val color = if (highlighted) StandbyBlue else TextDark
    OutlinedButton(
        onClick = { /* A-13 只做外觀 */ },
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            if (highlighted) 2.dp else 1.dp,
            if (highlighted) StandbyBlue else BorderGray
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = color
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

// ④ STOP：黃色外框 + 紅色大按鈕（急停功能之後的票再做）
@Composable
fun StopButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(StopYellow, RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(72.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StopRed,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Outlined.PanTool, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text("STOP", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA, widthDp = 380)
@Composable
private fun BottomPreview() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        BottomButtons()
        StopButton(onClick = {})
    }
}

// ③ Connection 面板（假資料，寫死）
@Composable
fun ConnectionPanel(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    latencyMs: Long? = null,
    stateAgeMs: Long? = null
) {
    var serverAddress by remember { mutableStateOf(DEFAULT_SERVER_URL.removePrefix("ws://")) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PanelBg, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Connection", color = TextDark, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        StatusRow(
            label = "Latency",
            value = when {
                latencyMs == null -> "—"
                latencyMs < 400 -> "$latencyMs ms, normal"
                else -> "$latencyMs ms, slow"
            },
            ok = latencyMs != null && latencyMs < 400
        )
        StatusRow(
            label = "Robot heartbeat",
            value = when {
                stateAgeMs == null -> "Not received"
                stateAgeMs < 1000 -> "Received just now"
                else -> "${stateAgeMs / 1000} s ago"
            },
            ok = stateAgeMs != null && stateAgeMs < 1000
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Server address", color = TextMuted, fontSize = 12.sp)
            OutlinedTextField(
                value = serverAddress,
                onValueChange = { serverAddress = it },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TextMuted,
                    unfocusedBorderColor = CloseGray,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* 這期不做事 */ }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Change server", color = TextDark, fontSize = 15.sp,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("›", color = TextDark, fontSize = 18.sp)
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CloseGray,
                contentColor = TextDark
            )
        ) {
            Text("Close", fontSize = 16.sp)
        }
    }
}

// 綠點 + 粗體標籤 + 灰色說明
@Composable
private fun StatusRow(label: String, value: String, ok: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (ok) ConnectedGreen else StopRed, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(value, color = TextMuted, fontSize = 14.sp)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun ConnectionPanelPreview() {
    ConnectionPanel(onClose = {}, modifier = Modifier.padding(16.dp))
}

// ④ 急停頁：取代 CameraArea + BottomButtons
@Composable
fun EStopScreen(onRelease: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        EStopCard()
        Spacer(Modifier.weight(1f))
        HoldToReleaseButton(onRelease = onRelease)    // 先寫死 30%，下一步改成真的
        Spacer(Modifier.height(8.dp))
        Text(
            "Release early to cancel. A short buzz confirms release.",
            color = TextMuted, fontSize = 12.sp
        )
    }
}

@Composable
private fun EStopCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EStopPink, RoundedCornerShape(12.dp))
            .border(1.dp, EStopPinkBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.PanTool,
            contentDescription = null,
            tint = EStopRed,
            modifier = Modifier.size(32.dp)
        )
        Text("E-stopped", color = EStopRed, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Arm stopped. All inputs are locked.", color = TextDark, fontSize = 15.sp)
        Text("Volume keys and on-screen controls are ignored.", color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun HoldToReleaseButton(onRelease: () -> Unit, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }      // 0f ~ 1f，會動的數字
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color.White, RoundedCornerShape(10.dp))
            .border(1.dp, CloseGray, RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // ① 手指按下：開始跑 1 秒的進度
                        val job = scope.launch {
                            progress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
                            )
                            // ③ 跑滿了才會執行到這裡
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onRelease()
                        }
                        // ② 在這裡等，直到手指放開
                        tryAwaitRelease()
                        // ④ 手指放開了：如果還沒跑完就取消，進度歸零
                        job.cancel()
                        progress.snapTo(0f)
                    }
                )
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.5.dp,
            color = HoldOrange,
            trackColor = CloseGray
        )
        Spacer(Modifier.width(10.dp))
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = TextDark, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("Hold 1s to release", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA, heightDp = 600)
@Composable
private fun EStopScreenPreview() {
    EStopScreen(onRelease = {}, modifier = Modifier.padding(16.dp))
}