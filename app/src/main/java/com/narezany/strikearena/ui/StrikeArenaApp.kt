package com.narezany.strikearena.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.narezany.strikearena.model.*
import com.narezany.strikearena.network.EloMqtt
import com.narezany.strikearena.network.LanNetwork
import java.util.UUID

@Composable
fun StrikeArenaApp() {
    val uiHandler = Handler(Looper.getMainLooper())
    val engine = remember { GameEngine() }
    val lan = remember { LanNetwork(uiHandler) }
    val eloMqtt = remember { EloMqtt(uiHandler) }
    val deviceId by remember { mutableStateOf("dev_" + UUID.randomUUID().toString().substring(0, 8)) }

    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }
    var localName by remember { mutableStateOf("Игрок" + (Math.random() * 1000).toInt().toString().padStart(3, '0')) }
    var localSkin by remember { mutableStateOf("default") }
    var localWeapon by remember { mutableStateOf("ak47") }
    var coins by remember { mutableStateOf(1500) }
    var mode by remember { mutableStateOf(GameMode.VersusBot) }
    var botSettings by remember { mutableStateOf(BotSettings()) }
    var eloEnabled by remember { mutableStateOf(true) }
    var lanStatus by remember { mutableStateOf("") }
    var lanConnected by remember { mutableStateOf(false) }
    var myIp by remember { mutableStateOf(getLocalIp()) }
    var eloEntries = remember { mutableStateListOf<Pair<String, Int>>() }
    var yourElo by remember { mutableIntStateOf(1000) }
    var eloStatus by remember { mutableStateOf("") }
    var eloConnected by remember { mutableStateOf(false) }

    // инициализировать локального игрока
    LaunchedEffect(Unit) {
        engine.setLocal(localName, localSkin, localWeapon, deviceId)
    }

    // ---------- ЭЛО ----------
    LaunchedEffect(eloMqtt) {
        eloMqtt.onLeaderboard = { entry ->
            val name = entry.name
            val idx = eloEntries.indexOfFirst { it.first == name }
            if (idx >= 0) eloEntries[idx] = Pair(name, entry.elo)
            else eloEntries.add(Pair(name, entry.elo))
            eloEntries.clear()
            eloEntries.addAll(eloEntries.sortedByDescending { it.second })
            val me = eloEntries.firstOrNull { it.first == localName }
            if (me != null) yourElo = me.second
        }
        eloMqtt.onStatus = { eloStatus = it; eloConnected = it.contains("онлайн") }
    }

    fun startGame() {
        engine.setLocal(localName, localSkin, localWeapon, deviceId)
        engine.start(mode, 800f, 600f, botSettings, eloEnabled)
        if (mode != GameMode.VersusBot && lan.isHost) {
            lan.broadcast(LanMsg.StartGame)
        }
    }

    // ---------- ЛАН (единый обработчик сообщений) ----------
    lan.onMessage = { msg ->
        when (msg) {
            is LanMsg.Join -> {
                if (msg.id != deviceId) {
                    val s = engine.session
                    if (!s.players.containsKey(msg.id)) {
                        val w = weaponById(msg.weapon)
                        s.players[msg.id] = Player(
                            id = msg.id, name = msg.name,
                            color = skinById(msg.skin).color,
                            weaponId = msg.weapon,
                            ammo = w.maxAmmo, maxAmmo = w.maxAmmo,
                            isLocal = false, x = 220f, y = 220f)
                    }
                }
                lanStatus = "Игрок: ${msg.name}"
            }
            is LanMsg.State -> {
                val s = engine.session
                if (msg.id != deviceId) {
                    val p = s.players[msg.id] ?: Player(msg.id, "Игрок", isLocal = false, x = 220f, y = 220f)
                    p.x = msg.x; p.y = msg.y; p.angle = msg.angle
                    p.hp = msg.hp; p.weaponId = msg.weaponId
                    p.score = msg.score; p.kills = msg.kills; p.deaths = msg.deaths
                    p.alive = true
                    s.players[msg.id] = p
                } else {
                    val me = engine.localPlayer
                    me.x = msg.x; me.y = msg.y; me.angle = msg.angle
                    me.hp = msg.hp
                    me.score = msg.score; me.kills = msg.kills; me.deaths = msg.deaths
                }
            }
            is LanMsg.Shoot -> {
                val w = weaponById("ak47")
                engine.session.bullets.add(Bullet(
                    id = msg.bulletId, ownerId = msg.id,
                    x = msg.bx, y = msg.by, vx = msg.vx, vy = msg.vy,
                    damage = w.damage, rangeLeft = w.range,
                    bornAt = System.currentTimeMillis().toDouble() / 1000.0,
                ))
            }
            is LanMsg.Hit -> {
                val t = engine.session.players[msg.targetId]
                if (t != null) {
                    t.hp -= msg.damage
                    if (t.hp <= 0) { t.alive = false; t.deaths++ }
                }
            }
            is LanMsg.StartGame -> {
                if (screen != Screen.Game) {
                    startGame()
                    screen = Screen.Game
                }
            }
            is LanMsg.Respawn -> {
                val p = engine.session.players[msg.id]
                if (p != null) { p.alive = true; p.hp = 100f; p.x = 150f; p.y = 150f }
            }
            is LanMsg.Leave -> {
                engine.session.players.remove(msg.id)
            }
        }
    }

    fun startGame() {
        engine.setLocal(localName, localSkin, localWeapon, deviceId)
        engine.start(mode, 800f, 600f, botSettings, eloEnabled)
        if (mode != GameMode.VersusBot && lan.isHost) {
            lan.broadcast(LanMsg.StartGame)
        }
    }

    when (screen) {
        Screen.Menu -> MenuScreen(
            onPlay = { screen = Screen.Play },
            onSkins = { screen = Screen.Skins },
            onSettings = { screen = Screen.Settings },
            onElo = {
                screen = Screen.Elo
                eloMqtt.connect()
                eloMqtt.requestLeaderboard()
            },
            onExit = { },
        )
        Screen.Play -> PlaySubmenu(
            onBack = { screen = Screen.Menu },
            onBot = { screen = Screen.ModeSelect },
            onLan = { screen = Screen.Lobby },
        )
        Screen.Skins -> SkinsScreen(
            onBack = { screen = Screen.Menu },
            currentSkin = localSkin,
            onSelect = { localSkin = it },
            coins = coins,
            onAddCoins = { coins += 500 },
        )
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Menu })
        Screen.Lobby -> LobbyScreen(
            onBack = { screen = Screen.Menu; lan.disconnect() },
            onStartHost = {
                lan.startHost()
                lanConnected = true
                lanStatus = "Хост. IP: $myIp"
                startGame()
                screen = Screen.Game
            },
            onJoin = { ip ->
                lanStatus = "Подключение к $ip..."
                lan.connect(ip, myId = deviceId, name = localName, skin = localSkin, weapon = localWeapon)
                lanConnected = true
                startGame()
                screen = Screen.Game
            },
            status = lanStatus,
            myIp = myIp,
            connected = lanConnected,
        )
        Screen.Elo -> EloScreen(
            onBack = { screen = Screen.Menu; eloMqtt.disconnect() },
            onRefresh = { eloMqtt.requestLeaderboard() },
            entries = eloEntries.toList(),
            yourName = localName,
            yourElo = yourElo,
            status = eloStatus,
            onConnect = { eloMqtt.connect() },
            connected = eloConnected,
        )
        Screen.ModeSelect -> ModeSelectScreen(
            onBack = { screen = Screen.Play },
            onStartBot = { dif, cnt ->
                botSettings = BotSettings(dif, cnt)
                mode = GameMode.VersusBot
                eloEnabled = false
                startGame()
                screen = Screen.Game
            },
            onStartLan = { dif, cnt, compType ->
                botSettings = BotSettings(dif, cnt)
                when (compType) {
                    "comp" -> { mode = GameMode.LanCompetitive; eloEnabled = true }
                    "alt" -> { mode = GameMode.LanAlt; eloEnabled = false }
                    "duel" -> { mode = GameMode.LanDuel; eloEnabled = false }
                }
                screen = Screen.Lobby
            },
        )
        Screen.Game -> GameScreen(
            engine = engine,
            lan = if (mode == GameMode.VersusBot) null else lan,
            eloMqtt = eloMqtt,
            mode = mode,
            localName = localName,
            localSkin = localSkin,
            localWeapon = localWeapon,
            botSettings = botSettings,
            eloEnabled = eloEnabled,
            onBack = {
                screen = Screen.Menu
                lan.disconnect()
                engine.stop()
            },
            onModeResult = { screen = Screen.Play },
            uiHandler = uiHandler,
        )
    }
}

private enum class Screen {
    Menu, Play, Skins, Settings, Lobby, Elo, ModeSelect, Game
}

@Composable
fun ModeSelectScreen(
    onBack: () -> Unit,
    onStartBot: (BotDifficulty, Int) -> Unit,
    onStartLan: (BotDifficulty, Int, String) -> Unit,
) {
    var dif by remember { mutableStateOf(BotDifficulty.Medium) }
    var cnt by remember { mutableStateOf(6) }
    var comp by remember { mutableStateOf("comp") }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F19.toInt())),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "НАСТРОЙКИ СЕССИИ", style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFFFF6B1A.toInt())))
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Сложность ботов: ${dif.name}", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
        Slider(value = dif.ordinal.toFloat() + 0.5f, onValueChange = {
            dif = BotDifficulty.values()[(it - 0.5f).toInt().coerceIn(0, 2)]
        }, valueRange = 0.5f..2.5f, steps = 2)
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Количество ботов: $cnt", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White))
        Slider(value = cnt.toFloat(), onValueChange = { cnt = it.toInt().coerceIn(1, 10) }, valueRange = 1f..10f, steps = 9)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Тип соревнования:", style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF9AA5B1.toInt())))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (t in listOf("comp" to "ЭЛО", "alt" to "АЛЬТ (нет ЭЛО)", "duel" to "ДУЭЛЬ 1x1")) {
                val selected = comp == t.first
                Button(onClick = { comp = t.first },
                    colors = ButtonDefaults.buttonColors(containerColor = if (selected) Color(0xFFFF6B1A.toInt()) else Color(0xFF161B26.toInt()))) {
                    Text(text = t.second, color = Color.Black)
                }
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onStartBot(dif, cnt) }, modifier = Modifier.width(210.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                Text(text = "ПРОТИВ БОТА", color = Color.White)
            }
            Button(onClick = { onStartLan(dif, cnt, comp) }, modifier = Modifier.width(210.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                Text(text = "ЧЕРЕЗ LAN", color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.width(200.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
            Text(text = "НАЗАД", color = Color.White)
        }
    }
}
