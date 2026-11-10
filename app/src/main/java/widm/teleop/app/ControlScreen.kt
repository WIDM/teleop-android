package widm.teleop.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton

// 先把顏色集中寫在這裡，之後要改色只改這邊
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

// 整頁：由上到下 ① 頂部列 ② 視角區 ③ 三個按鈕 ④ STOP
@Composable
fun ControlScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PageBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TopBar()
        CameraArea(Modifier.weight(1f))   // 佔掉剩下的高度
        BottomButtons()
        StopButton()
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 840)
@Composable
private fun ControlScreenPreview() {
    ControlScreen()
}

// ① 頂部列：左邊 Standby，右邊 Connected（假資料，寫死）
@Composable
fun TopBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 藍色膠囊標籤
        Text(
            text = "Standby",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(StandbyBlue, CircleShape)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        // 把右邊的東西推到最右側
        Spacer(Modifier.weight(1f))

        // 綠點 + Connected + 箭頭
        Box(Modifier.size(8.dp).background(ConnectedGreen, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text("Connected", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        Text("›", color = TextDark, fontSize = 18.sp)
    }
}

// 只在 Android Studio 預覽用，不會出現在 app 裡
@Preview(showBackground = true, backgroundColor = 0xFFF5F7FA)
@Composable
private fun TopBarPreview() {
    TopBar(Modifier.padding(16.dp))
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
fun StopButton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(StopYellow, RoundedCornerShape(16.dp))
            .padding(8.dp)
    ) {
        Button(
            onClick = { /* A-13 只做外觀 */ },
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
        StopButton()
    }
}