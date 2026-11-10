package com.narezany.strikearena.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.FontWeight
import androidx.compose.ui.text.TextStyle
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
) {
    val session = engine.getSession
    val me = engine.localPlayer

    // инициируем сессию при входе
    LaunchedEffect(Unit) {
        engine.start(mode, 800f, 600f, botSettings, eloEnabled)
        if (mode != GameMode.VersusBot && lan != null && lan.isHost) {
            lan.broadcast(LanMsg.StartGame)
        }
    }

    // игровой тик
    var last by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val dt = ((now - last).coerceIn(5.0, 50.0).toDouble() / 1000.0)
            last = now
            engine.tick(dt, now)
            engine.respawnIfNeeded(now)
            if (session.finished && mode == GameMode.LanCompetitive && eloEnabled) {
                eloMqtt?.publishElo(localName, me.score)
            }
            Thread.sleep(16)
        }
    }

    // LAN: отправка своего состояния
    if (lan != null) {
        LaunchedEffect(lan) {
            var lastSend = 0L
            while (true) {
                val now = System.currentTimeMillis()
                if (now - lastSend >= 50) {
                    lastSend = now
                    val p = me
                    val msg = LanMsg.State(
                        id = p.id, x = p.x, y = p.y, angle = p.angle,
                        hp = p.hp, weaponId = p.weaponId, score = p.score, kills = p.kills, deaths = p.deaths)
                    if (lan.isHost) lan.broadcast(msg, exclude = p.id)
                    else lan.send(msg, p.id)
                }
                Thread.sleep(20)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F19.toInt())),
    ) {
        // арена
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
                .background(Color(0xFF0E131F.toInt())),
        ) {
            GameField(session = session, me = me)
        }

        // HUD
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Text(text = "HP ${me.hp.toInt()}", style = TextStyle(fontSize = 20.sp, color = Color(0xFFFF6B1A.toInt())))
            Text(text = "${weaponById(me.weaponId).name.toUpperCase()}  ${me.ammo}/${me.maxAmmo}",
                style = TextStyle(fontSize = 14.sp, color = Color(0xFFF1C40F.toInt())))
            Text(text = "УБИЙСТВ: ${me.kills}  СМЕРТЕЙ: ${me.deaths}  СЧЁТ: ${me.score}",
                style = TextStyle(fontSize = 13.sp, color = Color.White))
            Text(text = "${mode.name} | БОТОВ: ${session.bots.count { it.alive }}",
                style = TextStyle(fontSize = 12.sp, color = Color(0xFF9AA5B1.toInt())))
        }

        // управление (кнопки вместо джойстиков — без pointerInput)
        Row(
            modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MoveButtons(onDirectionChange = { dx, dy ->
                engine.moveX = dx; engine.moveY = dy
            })
            Spacer(modifier = Modifier.width(30.dp))
            ShootButton(onShoot = { engine.shooting = true })
        }

        Box(
            modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Меню", tint = Color.White)
            }
        }

        if (session.finished) {
            EndScreen(
                me = me,
                session = session,
                mode = mode,
                onRestart = {
                    engine.start(mode, 800f, 600f, botSettings, eloEnabled)
                },
                onMenu = { onBack(); onModeResult() },
            )
        }
    }
}

@Composable
private fun GameField(session: GameSession, me: Player) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // боты
        for (b in session.bots.filter { it.alive }) {
            BotView(bot = b, me = me)
        }
        // пули
        for (b in session.bullets) {
            BulletView(bullet = b, me = me)
        }
        // игроки (кроме себя)
        for (p in session.players.values.filter { it.alive && p.id != me.id }) {
            PlayerView(player = p, me = me, isLocal = false)
        }
        // свой игрок поверх
        PlayerView(player = me, me = me, isLocal = true)
    }
}

private fun ox(p: Player, me: Player): Float = (p.x - me.x) + 200f
private fun oy(p: Player, me: Player): Float = (p.y - me.y) + 150f
private fun obx(b: Bullet, me: Player): Float = (b.x - me.x) + 200f
private fun oby(b: Bullet, me: Player): Float = (b.y - me.y) + 150f

@Composable
private fun PlayerView(player: Player, me: Player, isLocal: Boolean) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(Color(player.color))
            .offset { IntOffset(ox(player, me).roundToInt(), oy(player, me).roundToInt()) },
    ) {
        if (player.hp < player.maxHp) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(22.dp, 3.dp)
                    .background(Color(0xFF7F8C8D.toInt()))
                    .offset { IntOffset(2, -6) },
            ) {
                Box(
                    modifier = Modifier
                        .width(((player.hp / player.maxHp) * 22).roundToInt().dp)
                        .background(Color(0xFFE74C3C.toInt())),
                )
            }
        }
        Text(
            text = player.name.take(6),
            style = TextStyle(fontSize = 8.sp, color = Color.White),
            modifier = Modifier.align(Alignment.BottomCenter).offset { IntOffset(0, 28) },
        )
    }
}

@Composable
private fun BotView(bot: Bot, me: Player) {
    PlayerView(
        player = Player(
            id = bot.id, name = "Бот", color = bot.color, weaponId = bot.weaponId,
            hp = bot.hp, maxHp = bot.maxHp, ammo = 0, maxAmmo = 0,
            score = 0, kills = 0, deaths = 0,
            isLocal = false, x = bot.x, y = bot.y, angle = bot.angle, alive = bot.alive),
        me = me, isLocal = false)
}

@Composable
private fun BulletView(bullet: Bullet, me: Player) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(Color(0xFFFF6B1A.toInt()))
            .offset { IntOffset(obx(bullet, me).roundToInt(), oby(bullet, me).roundToInt()) },
    )
}

@Composable
private fun MoveButtons(onDirectionChange: (Float, Float) -> Unit) {
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    Column(
        modifier = Modifier.size(110.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.Center) {
            DirBtn("↑", pressed = dy < 0, onPress = { dy = -1f; dx = 0f }, onRelease = { dy = 0f })
        }
        Row(horizontalArrangement = Arrangement.Center) {
            DirBtn("←", pressed = dx < 0, onPress = { dx = -1f; dy = 0f }, onRelease = { dx = 0f })
            DirBtn("■", pressed = false, onPress = { dx = 0f; dy = 0f }, onRelease = {})
            DirBtn("→", pressed = dx > 0, onPress = { dx = 1f; dy = 0f }, onRelease = { dx = 0f })
        }
        Row(horizontalArrangement = Arrangement.Center) {
            DirBtn("↓", pressed = dy > 0, onPress = { dy = 1f; dx = 0f }, onRelease = { dy = 0f })
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            onDirectionChange(dx, dy)
            Thread.sleep(50)
        }
    }
}

@Composable
private fun DirBtn(label: String, pressed: Boolean, onPress: () -> Unit, onRelease: () -> Unit) {
    Button(
        onClick = { onPress(); onRelease() },
        colors = ButtonDefaults.buttonColors(containerColor = if (pressed) Color(0xFFFF6B1A.toInt()) else Color(0xFF161B26.toInt())),
        modifier = Modifier.size(40.dp),
    ) {
        Text(text = label, color = Color.White)
    }
}

@Composable
private fun ShootButton(onShoot: () -> Unit) {
    Button(
        onClick = { onShoot() },
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE74C3C.toInt())),
        modifier = Modifier.size(56.dp),
    ) {
        Icon(imageVector = Icons.Default.FiberManualRecord, contentDescription = "огонь", tint = Color.White)
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
        Box(
            modifier = Modifier.fillMaxWidth(0.8f)
                .background(Color(0xCC0B0F19.toInt()))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (session.winner == me.id) "ПОБЕДА!" else "ПРОИГРЫШ",
                    style = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        color = if (session.winner == me.id) Color(0xFF2ECC71.toInt()) else Color(0xFFE74C3C.toInt())))
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "УБИЙСТВ: ${me.kills}   СМЕРТЕЙ: ${me.deaths}   СЧЁТ: ${me.score}",
                    style = TextStyle(fontSize = 16.sp, color = Color.White))
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRestart, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                        Text(text = "СНОВА", color = Color.White)
                    }
                    Button(onClick = onMenu, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                        Text(text = "МЕНЮ", color = Color.White)
                    }
                }
            }
        }
    }
}
