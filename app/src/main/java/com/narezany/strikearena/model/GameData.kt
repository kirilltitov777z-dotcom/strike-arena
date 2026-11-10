package com.narezany.strikearena.model

import kotlin.math.*
import java.io.Serializable

/** Оружие, доступное в игре (как в видео). */
data class Weapon(
    val id: String,
    val name: String,
    val damage: Int,
    val range: Float,      // метры
    val fireRate: Float,   // выстрелов в секунду
    val bulletSpeed: Float,
    val bulletSize: Float,
    val spread: Float,     // разброс (0..1)
    val reloadSec: Float,
    val maxAmmo: Int,
    val color: Int,
    val icon: String,
)

val WEAPONS = listOf(
    Weapon("knife", "Нож", 999, 1.8f, 2.0f, 0f, 0f, 0f, 0f, 999, 0xFF2ECC71.toInt(), "🔪"),
    Weapon("pistol", "Пистолет", 35, 22f, 4f, 18f, 3f, 0.12f, 1.5f, 18, 0xFF3498DB.toInt(), "🔫"),
    Weapon("shotgun", "Дробовик", 18, 11f, 2.2f, 15f, 4f, 0.45f, 2.5f, 8, 0xFF9B59B6.toInt(), "🏹"),
    Weapon("smg", "ПП (MP5)", 24, 18f, 11f, 16f, 3f, 0.2f, 1.8f, 35, 0xFFE67E22.toInt(), "💥"),
    Weapon("ak47", "Калаш (AK-47)", 32, 30f, 8.5f, 19f, 3f, 0.22f, 2.2f, 30, 0xFFE74C3C.toInt(), "🔫"),
    Weapon("m4", "M4A1", 30, 32f, 9f, 19f, 3f, 0.18f, 2.0f, 30, 0xFF34495E.toInt(), "🔫"),
    Weapon("awp", "Снайпер (AWP)", 120, 60f, 1.6f, 26f, 4f, 0.05f, 3.0f, 10, 0xFFF1C40F.toInt(), "🎯"),
    Weapon("augs", "AUG", 28, 30f, 10f, 19f, 3f, 0.16f, 2.1f, 30, 0xFF1ABC9C.toInt(), "🔫"),
    Weapon("g3sg1", "G3SG1", 45, 45f, 3.5f, 22f, 4f, 0.1f, 2.6f, 10, 0xFFD35400.toInt(), "🎯"),
)

fun weaponById(id: String): Weapon = WEAPONS.firstOrNull { it.id == id } ?: WEAPONS[1]

/** Скин игрока (меняет цвет и внешний вид). */
data class Skin(
    val id: String,
    val name: String,
    val color: Int,
    val locked: Boolean = false,
    val price: Int = 0,
)

val SKINS = listOf(
    Skin("default", "Стандарт", 0xFF3498DB.toInt()),
    Skin("ghost", "Призрак", 0xFF7F8C8D.toInt(), locked = true, price = 250),
    Skin("gold", "Золото", 0xFFF1C40F.toInt(), locked = true, price = 750),
    Skin("blood", "Кровь", 0xFFC0392B.toInt(), locked = true, price = 500),
    Skin("neon", "Неон", 0xFF00E5FF.toInt(), locked = true, price = 1000),
    Skin("butterfly", "Бабочка", 0xFF9B59B6.toInt(), locked = true, price = 1200),
)

fun skinById(id: String): Skin = SKINS.firstOrNull { it.id == id } ?: SKINS[0]

/** Игрок в сессии. */
data class Player(
    val id: String,
    val name: String,
    var x: Float = 0f,
    var y: Float = 0f,
    var angle: Float = 0f,
    var hp: Float = 100f,
    var maxHp: Float = 100f,
    var color: Int = 0xFF3498DB.toInt(),
    var weaponId: String = "ak47",
    var ammo: Int = 30,
    var maxAmmo: Int = 30,
    var score: Int = 0,
    var kills: Int = 0,
    var deaths: Int = 0,
    var lastShot: Double = 0.0,
    var reloading: Boolean = false,
    var reloadStart: Double = 0.0,
    var isLocal: Boolean = false,
    var alive: Boolean = true,
    var respawnAt: Double = 0.0,
)

/** Пуля. */
data class Bullet(
    val id: String,
    val ownerId: String,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Int,
    var rangeLeft: Float,
    val bornAt: Double,
)

/** Бот. */
data class Bot(
    val id: String,
    var x: Float,
    var y: Float,
    var angle: Float,
    var hp: Float = 100f,
    var maxHp: Float = 100f,
    var color: Int = 0xFFE74C3C.toInt(),
    var weaponId: String = "ak47",
    var lastShot: Double = 0.0,
    var target: String? = null,
    var targetX: Float = 0f,
    var targetY: Float = 0f,
    var nextDecision: Double = 0.0,
    var alive: Boolean = true,
    val difficulty: BotDifficulty = BotDifficulty.Medium,
)

enum class BotDifficulty { Easy, Medium, Hard }

/** Режим игры. */
enum class GameMode {
    VersusBot,          // против бота (одиночная)
    LanCompetitive,     // ЛАН, соревновательный (ЭЛО)
    LanAlt,             // ЛАН альт (без ЭЛО, сложность ботов)
    LanDuel,            // ЛАН дуэль 1x1 (сложность бота)
}

/** Настройки сложности ботов. */
data class BotSettings(val difficulty: BotDifficulty = BotDifficulty.Medium, val count: Int = 6)

/** Состояние сессии. */
data class GameSession(
    var mode: GameMode = GameMode.VersusBot,
    var players: MutableMap<String, Player> = mutableMapOf(),
    var bullets: MutableList<Bullet> = mutableListOf(),
    var bots: MutableList<Bot> = mutableListOf(),
    var localId: String = "",
    var localName: String = "Игрок",
    var localSkin: String = "default",
    var localWeapon: String = "ak47",
    var width: Float = 800f,
    var height: Float = 600f,
    var timeLeft: Int = 180,
    var started: Boolean = false,
    var finished: Boolean = false,
    var winner: String? = null,
    var botSettings: BotSettings = BotSettings(),
    var eloEnabled: Boolean = true,
)

/** Сетевое сообщение (LAN). */
sealed interface LanMsg : Serializable {
    data class Join(val id: String, val name: String, val skin: String, val weapon: String) : LanMsg
    data class Leave(val id: String) : LanMsg
    data class State(val id: String, val x: Float, val y: Float, val angle: Float,
                     val hp: Float, val weaponId: String, val score: Int, val kills: Int, val deaths: Int) : LanMsg
    data class Shoot(val id: String, val x: Float, val y: Float, val angle: Float,
                     val bulletId: String, val bx: Float, val by: Float, val vx: Float, val vy: Float) : LanMsg
    data class Hit(val targetId: String, val attackerId: String, val damage: Int) : LanMsg
    object StartGame : LanMsg
    data class Respawn(val id: String) : LanMsg
}

object MapUtils {
    fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Double =
        hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
    fun angleTo(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()
}
