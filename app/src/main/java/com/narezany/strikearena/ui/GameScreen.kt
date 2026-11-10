package com.narezany.strikearena.ui

import android.os.Handler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pointer.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narezany.strikearena.model.*
import com.narezany.strikearena.network.EloMqtt
import com.narezany.strikearena.network.LanNetwork
import kotlin.math.*

@Composable
fun GameScreen(
    engine: GameEngine,
    lan: LanNetwork?,
    eloMqtt: EloMqtt?,
    mode: GameMode,
    localName: String,
    localSkin: String,
    localWeapon: String,
    botSettings: BotSettings,
    eloEnabled: Boolean,
    onBack: () -> Unit,
    onModeResult: () -> Unit,
    uiHandler: Handler,
) {
    val me = engine.localPlayer
    val session = engine.getSession

    var playW by remember { mutableFloatStateOf(800f) }
    var playH by remember { mutableFloatStateOf(600f) }

    val showEndValue by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(Offset.Zero) }
    var aim by remember { mutableStateOf(Offset.Zero) }
    var shooting by remember { mutableStateOf(false) }
    var lastSend by remember { mutableStateOf(0.0) }

    engine.switchWeapon = { id ->
        me.weaponId = id
        me.ammo = weaponById(id).maxAmmo
        me.maxAmmo = weaponById(id).maxAmmo
    }

    LaunchedEffect(Unit) {
        var last = System.currentTimeMillis().toDouble() / 1000.0
        while (true) {
            val now = System.currentTimeMillis().toDouble() / 1000.0
            val dt = (now - last).coerceIn(0.005, 0.05)
            last = now
            engine.moveX = move.x.toFloat()
            engine.moveY = move.y.toFloat()
            engine.aimX = aim.x.toFloat()
            engine.aimY = aim.y.toFloat()
            engine.shooting = shooting
            engine.tick(dt, now)
            engine.respawnIfNeeded(now)

            if (lan != null && now - lastSend >= 0.05) {
                lastSend = now
                val p = me
                val msg = LanMsg.State(
                    id = p.id, x = p.x, y = p.y, angle = p.angle,
                    hp = p.hp, weaponId = p.weaponId, score = p.score, kills = p.kills, deaths = p.deaths)
                if (lan.isHost) {
                    lan.broadcast(msg, exclude = p.id)
                } else {
                    lan.send(msg, p.id)
                }
            }

            if (session.finished && !showEndValue) {
                // cannot mutate showEndValue here directly; handled via recomposition reading session.finished
            }
            Thread.sleep(16)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F19))
    ) {
        val playMod = Modifier
            .weight(1f)
            .clipToBounds()
            .background(Color(0xFF0E131F))
            .border(2.dp, Color(0xFF2A3242), RoundedCornerShape(8.dp))
            .padding(16.dp)
            .onSizeChanged { size ->
                playW = size.width.toFloat()
                playH = size.height.toFloat()
            }

        Box(modifier = playMod) {
            GameCanvas(
                session = session,
                me = me,
                camX = me.x - playW / 2,
                camY = me.y - playH / 2,
                width = playW,
                height = playH,
            )
            HUD(me = me, weapon = weaponById(me.weaponId), mode = mode,
                timeLeft = session.timeLeft,
                botsAlive = session.bots.count { it.alive })
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(170.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Joystick(value = move, onChange = { move = it }, label = "ДВИЖ.", size = 130.dp)
            Spacer(modifier = Modifier.width(40.dp))
            Joystick(value = aim, onChange = { aim = it }, label = "ПРИЦЕЛ", size = 140.dp)
            Spacer(modifier = Modifier.width(24.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FireButton(pressed = shooting, onPressedChange = { shooting = it })
                ReloadButton(onReload = { engine.forceReload(me) })
                WeaponSwitch(engine = engine, me = me)
            }
        }

        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Меню", tint = Color.White)
            }
        }

        // экран fin détecté via session.finished (mutableState → recomposition)
        if (session.finished) {
            EndScreen(
                me = me,
                session = session,
                mode = mode,
                onRestart = {
                    engine.start(mode, playW, playH, botSettings, eloEnabled)
                },
                onMenu = { onBack(); onModeResult() },
            )
        }
    }
}

@Composable
private fun GameCanvas(
    session: GameSession,
    me: Player,
    camX: Float,
    camY: Float,
    width: Float,
    height: Float,
) {
    var s by remember { mutableStateOf(session) }
    s = session
    Canvas(
        modifier = Modifier.fillMaxSize(),
        onDraw = {
            drawRect(color = Color(0xFF0E131F.toInt()), size = size)
            drawLine(color = Color(0xFF2A3242.toInt()), strokeWidth = 4f, start = Offset(0f, 0f), end = Offset(size.width, 0f))
            drawLine(color = Color(0xFF2A3242.toInt()), strokeWidth = 4f, start = Offset(0f, size.height), end = Offset(size.width, size.height))
            drawLine(color = Color(0xFF2A3242.toInt()), strokeWidth = 4f, start = Offset(0f, 0f), end = Offset(0f, size.height))
            drawLine(color = Color(0xFF2A3242.toInt()), strokeWidth = 4f, start = Offset(size.width, 0f), end = Offset(size.width, size.height))

            drawBullets(s.bullets, camX, camY)
            drawBots(s.bots, camX, camY)
            drawPlayers(s.players, me.id, camX, camY)
            val local = s.players[me.id] ?: me
            drawPlayer(local, camX, camY, isLocal = true)
            miniMap(s, size)
        },
    )
}

private fun DrawScope.drawBullets(bullets: List<Bullet>, camX: Float, camY: Float) {
    for (b in bullets) {
        drawCircle(color = Color(0xFFFF6B1A.toInt()), radius = 4f, center = Offset(b.x - camX, b.y - camY))
    }
}

private fun DrawScope.drawBots(bots: List<Bot>, camX: Float, camY: Float) {
    for (b in bots.filter { it.alive }) {
        drawCircle(color = Color(0xFFE74C3C.toInt()), radius = 14f, center = Offset(b.x - camX, b.y - camY))
        val hpPct = (b.hp / b.maxHp).coerceIn(0f, 1f)
        drawLine(color = Color.Red, strokeWidth = 3f,
            start = Offset(b.x - camX - 12, b.y - camY - 22),
            end = Offset(b.x - camX - 12 + 24 * hpPct, b.y - camY - 22))
    }
}

private fun DrawScope.drawPlayers(players: Map<String, Player>, localId: String, camX: Float, camY: Float) {
    for (p in players.values.filter { it.id != localId && it.alive }) {
        drawCircle(color = Color(p.color), radius = 13f, center = Offset(p.x - camX, p.y - camY))
        drawCircle(color = Color.White, radius = 2.5f, center = Offset(p.x - camX + 9, p.y - camY))
        val hpPct = (p.hp / p.maxHp).coerceIn(0f, 1f)
        drawLine(color = Color.Red, strokeWidth = 3f,
            start = Offset(p.x - camX - 11, p.y - camY - 20),
            end = Offset(p.x - camX - 11 + 22 * hpPct, p.y - camY - 20))
        drawText(text = p.name, x = p.x - camX - 20, y = p.y - camY - 26,
            style = TextStyle(fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold))
    }
}

private fun DrawScope.drawPlayer(p: Player, camX: Float, camY: Float, isLocal: Boolean) {
    drawCircle(color = Color(p.color), radius = if (isLocal) 15f else 13f, center = Offset(p.x - camX, p.y - camY))
    val endX = p.x + cos(p.angle.toDouble()).toFloat() * 20
    val endY = p.y + sin(p.angle.toDouble()).toFloat() * 20
    drawLine(color = Color.White, strokeWidth = 4f,
        start = Offset(p.x - camX, p.y - camY), end = Offset(endX - camX, endY - camY))
    val hpPct = (p.hp / p.maxHp).coerceIn(0f, 1f)
    drawLine(color = Color.Red, strokeWidth = 3f,
        start = Offset(p.x - camX - 13, p.y - camY - 22),
        end = Offset(p.x - camX - 13 + 26 * hpPct, p.y - camY - 22))
    drawText(text = p.name + (if (isLocal) " (ВЫ)" else ""),
        x = p.x - camX - 24, y = p.y - camY - 28,
        style = TextStyle(fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold))
    drawText(text = "${p.ammo}/${p.maxAmmo}",
        x = p.x - camX - 14, y = p.y - camY + 20,
        style = TextStyle(fontSize = 10.sp, color = Color(0xFFF1C40F.toInt()), fontWeight = FontWeight.Bold))
}

private fun DrawScope.miniMap(session: GameSession, size: Size) {
    val mmSize = 110f
    val mmX = size.width - mmSize - 8
    val mmY = size.height - mmSize - 8
    drawRect(color = Color(0x880B0F19.toInt()), size = Size(mmSize, mmSize), topLeft = Offset(mmX, mmY))
    val scale = mmSize / maxOf(session.width, session.height)
    for (p in session.players.values.filter { it.alive }) {
        drawRect(color = Color(if (p.id == session.localId) 0xFF00E5FF.toInt() else 0xFFE74C3C.toInt()),
            size = Size(3f, 3f), topLeft = Offset(mmX + p.x * scale, mmY + p.y * scale))
    }
    for (b in session.bots.filter { it.alive }) {
        drawRect(color = Color.Red, size = Size(2f, 2f), topLeft = Offset(mmX + b.x * scale, mmY + b.y * scale))
    }
}

@Composable
private fun Joystick(
    value: Offset,
    onChange: (Offset) -> Unit,
    label: String,
    size: Dp,
) {
    var pos by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = Modifier.size(size).pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { change, offset ->
                    active = true
                    val c = Offset(change.position.x - size.toPx() / 2, change.position.y - size.toPx() / 2)
                    pos = clampJoystick(c, size.toPx())
                    onChange(normalize(pos, size.toPx()))
                },
                onDrag = { change, offset ->
                    val c = Offset(change.position.x - size.toPx() / 2, change.position.y - size.toPx() / 2)
                    pos = clampJoystick(c, size.toPx())
                    onChange(normalize(pos, size.toPx()))
                },
                onDragEnd = {
                    active = false
                    pos = Offset.Zero
                    onChange(Offset.Zero)
                },
            )
        },
    ) {
        drawBehind {
            drawCircle(color = Color(0x33FFFFFF.toInt()), radius = size.toPx() / 2)
        }
        drawBehind {
            drawCircle(
                color = if (active) Color(0xFFFF6B1A.toInt()) else Color(0xFF9AA5B1.toInt()),
                radius = size.toPx() / 3,
                center = pos,
            )
        }
        Text(text = label, style = TextStyle(fontSize = 10.sp, color = Color(0xFF6B7280.toInt())),
            modifier = Modifier.align(Alignment.TopCenter), color = Color(0xFF6B7280.toInt()))
    }
}

private fun clampJoystick(p: Offset, r: Float): Offset {
    val d = hypot(p.x, p.y)
    if (d > r / 2) {
        val a = atan2(p.y, p.x)
        return Offset(cos(a) * r / 2, sin(a) * r / 2)
    }
    return p
}

private fun normalize(p: Offset, r: Float): Offset {
    val d = hypot(p.x, p.y)
    if (d == 0f) return Offset.Zero
    return Offset(p.x / (r / 2), p.y / (r / 2))
}

@Composable
private fun FireButton(pressed: Boolean, onPressedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier.size(64.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { onPressedChange(true) },
                    onRelease = { onPressedChange(false) },
                )
            }
            .background(if (pressed) Color(0xFFE74C3C.toInt()) else Color(0xFF161B26.toInt()), shape = CircleShape)
            .border(2.dp, Color(0xFFFF6B1A.toInt()), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = "огонь", tint = Color.White)
    }
}

@Composable
private fun ReloadButton(onReload: () -> Unit) {
    Button(
        onClick = onReload,
        modifier = Modifier.size(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt())),
    ) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = "перезарядка", tint = Color.White)
    }
}

@Composable
private fun WeaponSwitch(engine: GameEngine, me: Player) {
    val weapons = listOf("knife", "pistol", "ak47", "awp")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (w in weapons) {
            val sel = me.weaponId == w
            Button(
                onClick = { engine.switchWeapon(w) },
                colors = ButtonDefaults.buttonColors(containerColor = if (sel) Color(0xFFFF6B1A.toInt()) else Color(0xFF161B26.toInt())),
                modifier = Modifier.width(56.dp).height(40.dp),
            ) {
                Text(text = weaponById(w).icon, style = TextStyle(fontSize = 16.sp))
            }
        }
    }
}

@Composable
private fun HUD(me: Player, weapon: Weapon, mode: GameMode, timeLeft: Int, botsAlive: Int) {
    Column(
        modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
    ) {
        Text(text = "HP ${me.hp.toInt()}", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B1A.toInt())))
        Text(text = weapon.name.toUpperCase() + "  ${me.ammo}/${me.maxAmmo}", style = TextStyle(fontSize = 14.sp, color = Color(0xFFF1C40F.toInt())))
        Text(text = "УБИЙСТВ: ${me.kills}  СМЕРТЕЙ: ${me.deaths}  СЧЁТ: ${me.score}", style = TextStyle(fontSize = 13.sp, color = Color.White))
        Text(text = "${mode.name}  |  ВРЕМЯ: ${timeLeft / 60}:${timeLeft % 60.toString().padStart(2, '0')}  |  БОТОВ: $botsAlive",
            style = TextStyle(fontSize = 12.sp, color = Color(0xFF9AA5B1.toInt())))
    }
}

@Composable
private fun EndScreen(
    me: Player,
    session: GameSession,
    mode: GameMode,
    onRestart: () -> Unit,
    onMenu: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        drawBehind {
            drawRect(color = Color(0xCC0B0F19.toInt()), size = size)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = if (session.winner == me.id) "ПОБЕДА!" else "ПРОИГРЫШ",
                style = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (session.winner == me.id) Color(0xFF2ECC71.toInt()) else Color(0xFFE74C3C.toInt())))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "УБИЙСТВ: ${me.kills}   СМЕРТЕЙ: ${me.deaths}   СЧЁТ: ${me.score}",
                style = TextStyle(fontSize = 18.sp, color = Color.White))
            Spacer(modifier = Modifier.height(8.dp))
            if (mode == GameMode.LanCompetitive) {
                Text(text = "ЭЛО изменён", style = TextStyle(fontSize = 14.sp, color = Color(0xFFF1C40F.toInt())))
            }
            Spacer(modifier = Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onRestart, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                    Text(text = "СНОВА", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Button(onClick = onMenu, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                    Text(text = "МЕНЮ", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
