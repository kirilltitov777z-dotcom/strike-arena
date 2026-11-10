package com.narezany.strikearena.ui

import com.narezany.strikearena.model.*
import kotlin.math.*
import kotlin.random.Random
import java.util.UUID

/** Игровой движок (аутorité для хоста и для локальной сессии против бота). */
class GameEngine {
    val session = GameSession()
    val getSession: GameSession
        get() = session

    val localPlayer: Player
        get() = session.players[session.localId] ?: Player(session.localId, session.localName)

    var onEvent: ((String) -> Unit)? = null  // "kill", "death", "hit", "win"

    // ---- вход с экрана ----
    var moveX: Float = 0f
    var moveY: Float = 0f
    var aimX: Float = 0f
    var aimY: Float = 0f
    var shooting: Boolean = false
    var switchWeapon: (String) -> Unit = {}

    fun setLocal(name: String, skin: String, weapon: String, id: String) {
        session.localName = name
        session.localSkin = skin
        session.localWeapon = weapon
        session.localId = id
        val p = Player(id, name, color = skinById(skin).color, weaponId = weapon,
                       ammo = weaponById(weapon).maxAmmo, maxAmmo = weaponById(weapon).maxAmmo,
                       isLocal = true)
        session.players[id] = p
    }

    fun start(mode: GameMode, width: Float, height: Float, botSettings: BotSettings, eloEnabled: Boolean) {
        session.mode = mode
        session.width = width
        session.height = height
        session.botSettings = botSettings
        session.eloEnabled = eloEnabled
        session.started = true
        session.finished = false
        session.winner = null
        session.timeLeft = 180
        session.players.clear()
        session.bullets.clear()
        session.bots.clear()
        val me = session.players[session.localId]
        if (me == null) {
            setLocal(session.localName, session.localSkin, session.localWeapon, session.localId)
        }
        session.players[session.localId]!!.x = 150f
        session.players[session.localId]!!.y = 150f
        session.players[session.localId]!!.hp = 100f
        session.players[session.localId]!!.alive = true

        if (mode == GameMode.VersusBot || mode == GameMode.LanAlt || mode == GameMode.LanDuel) {
            spawnBots()
        }
    }

    private fun spawnBots() {
        val count = when (session.mode) {
            GameMode.LanDuel -> 1
            else -> session.botSettings.count.coerceAtLeast(1).coerceAtMost(10)
        }
        for (i in 0 until count) {
            val b = Bot(
                id = "bot_$i",
                x = Random.nextFloat() * (session.width - 100) + 50,
                y = Random.nextFloat() * (session.height - 100) + 50,
                angle = 0f,
                color = 0xFFE74C3C.toInt(),
                weaponId = Random.nextInt(WEAPONS.size).let { WEAPONS[it].id },
                difficulty = session.botSettings.difficulty,
            )
            session.bots.add(b)
        }
    }

    fun stop() {
        session.started = false
        session.finished = true
    }

    // ---- appelés chaque tick ----
    fun tick(dt: Double, now: Double) {
        if (!session.started || session.finished) return

        val me = localPlayer
        if (me.alive) {
            // mouvement
            val spd = 240f * dt.toFloat()
            var nx = me.x + moveX * spd
            var ny = me.y + moveY * spd
            nx = nx.coerceIn(30f, session.width - 30f)
            ny = ny.coerceIn(30f, session.height - 30f)
            me.x = nx; me.y = ny

            // visée
            if (aimX != 0f || aimY != 0f) {
                me.angle = atan2(aimY.toDouble(), aimX.toDouble()).toFloat()
            }

            // tir
            if (shooting && me.alive) {
                tryShoot(me, now)
            }

            // rechargement auto
            if (me.reloading && now - me.reloadStart >= weaponById(me.weaponId).reloadSec) {
                me.reloading = false
                me.ammo = me.maxAmmo
            }
        }

        // --- боты ---
        for (b in session.bots.filter { it.alive }) {
            botTick(b, dt, now)
        }

        // --- пули ---
        val aliveBullets = mutableListOf<Bullet>()
        for (b in session.bullets) {
            b.x += b.vx * dt.toFloat()
            b.y += b.vy * dt.toFloat()
            val ownerPlayer = session.players[b.ownerId]
            val dmg: Int
            if (ownerPlayer != null) {
                dmg = weaponById(ownerPlayer.weaponId).damage
            } else {
                val ownerBot = session.bots.firstOrNull { it.id == b.ownerId }
                dmg = if (ownerBot != null) weaponById(ownerBot.weaponId).damage else 10
            }
            var hit = false

            // joueurs
            for (p in session.players.values.filter { it.id != b.ownerId && it.alive }) {
                if (MapUtils.dist(b.x, b.y, p.x, p.y) < 22) {
                    applyDamage(p, dmg, b.ownerId, now)
                    hit = true
                    break
                }
            }
            if (!hit) {
                // боты
                for (bo in session.bots.filter { it.id != b.ownerId && it.alive }) {
                    if (MapUtils.dist(b.x, b.y, bo.x, bo.y) < 22) {
                        bo.hp -= dmg.toFloat()
                        if (bo.hp <= 0) { bo.alive = false; bo.hp = 0f; me.kills++; me.score += 100 }
                        hit = true
                        break
                    }
                }
            }

            b.rangeLeft -= (hypot(b.vx.toDouble(), b.vy.toDouble()) * dt).toFloat()
            if (!hit && b.rangeLeft > 0 && b.x >= 0f && b.x <= session.width && b.y >= 0f && b.y <= session.height) {
                aliveBullets.add(b)
            }
        }
        session.bullets.clear()
        session.bullets.addAll(aliveBullets)

        // --- réapparition des bots ---
        if ((session.mode == GameMode.LanAlt || session.mode == GameMode.LanDuel || session.mode == GameMode.VersusBot)
            && session.bots.count { it.alive } == 0) {
            spawnBots()
        }

        // --- timer ---
        session.timeLeft = maxOf(0, session.timeLeft - dt.toInt())
        if (session.timeLeft == 0) finishGame()
    }

    private fun tryShoot(p: Player, now: Double) {
        if (p.reloading || p.ammo <= 0 || now - p.lastShot < (1.0 / weaponById(p.weaponId).fireRate)) return
        val w = weaponById(p.weaponId)
        p.lastShot = now
        p.ammo--
        val spread = (Random.nextFloat() - 0.5f) * w.spread * 0.6f
        val ang = p.angle + spread
        val vx = cos(ang.toDouble()).toFloat() * w.bulletSpeed
        val vy = sin(ang.toDouble()).toFloat() * w.bulletSpeed
        session.bullets.add(Bullet(
            id = UUID.randomUUID().toString(), ownerId = p.id,
            x = p.x, y = p.y, vx = vx, vy = vy,
            damage = w.damage, rangeLeft = w.range, bornAt = now,
        ))
        // LAN: envoyer le tir
        onEvent?.invoke("shoot")
    }

    fun forceReload(p: Player) {
        if (!p.reloading && p.ammo < p.maxAmmo) {
            p.reloading = true
            p.reloadStart = System.currentTimeMillis() / 1000.0
        }
    }

    private fun botTick(b: Bot, dt: Double, now: Double) {
        // décision périodique
        if (now - b.nextDecision > (1.0 / (b.difficulty.ordinal + 1)) * 1.2) {
            b.nextDecision = now
            val me = localPlayer
            val target = session.players.values.filter { it.alive && it.id != b.id }
                .minByOrNull { MapUtils.dist(b.x, b.y, it.x, it.y) }
            if (target != null) {
                b.target = target.id
                b.targetX = target.x; b.targetY = target.y
                val a = atan2((target.y - b.y).toDouble(), (target.x - b.x).toDouble()).toFloat()
                b.angle = a
            }
        }
        val target = session.players[b.target ?: return]
        if (target == null || !target.alive) return
        // se rapprocher, mais garder une distance selon arme
        val w = weaponById(b.weaponId)
        val d = MapUtils.dist(b.x, b.y, target.x, target.y)
        val keep = w.range * 0.5f
        if (d > keep) {
            val spd = (180f + (b.difficulty.ordinal * 40f)) * dt.toFloat()
            val ang = atan2((target.y - b.y).toDouble(), (target.x - b.x).toDouble()).toFloat()
            b.x += cos(ang.toDouble()).toFloat() * spd
            b.y += sin(ang.toDouble()).toFloat() * spd
            b.x = b.x.coerceIn(30f, session.width - 30f)
            b.y = b.y.coerceIn(30f, session.height - 30f)
            b.angle = ang
        }
        // tir
        if (d <= w.range * 0.9f && now - b.lastShot > (1.0 / w.fireRate)) {
            b.lastShot = now
            val spread = if (b.difficulty == BotDifficulty.Easy) 0.35f
                else if (b.difficulty == BotDifficulty.Medium) 0.18f else 0.06f
            val s = (Random.nextFloat() - 0.5f) * spread
            val ang = b.angle + s
            val vx = cos(ang.toDouble()).toFloat() * w.bulletSpeed
            val vy = sin(ang.toDouble()).toFloat() * w.bulletSpeed
            session.bullets.add(Bullet(
                id = UUID.randomUUID().toString(), ownerId = b.id,
                x = b.x, y = b.y, vx = vx, vy = vy,
                damage = w.damage, rangeLeft = w.range, bornAt = now,
            ))
        }
    }

    private fun applyDamage(p: Player, dmg: Int, attackerId: String, now: Double) {
        p.hp -= dmg
        if (p.hp <= 0) {
            p.alive = false
            p.deaths++
            val attacker = session.players[attackerId]
            if (attacker != null) {
                attacker.kills++
                attacker.score += 200
            }
            p.respawnAt = now + 3.0
            onEvent?.invoke("death")
            // respawn auto
            // géré ci-dessous
        } else {
            onEvent?.invoke("hit")
        }
    }

    fun respawnIfNeeded(now: Double) {
        for (p in session.players.values) {
            if (!p.alive && now >= p.respawnAt) {
                p.alive = true
                p.hp = 100f
                p.x = Random.nextFloat() * (session.width - 100) + 50
                p.y = Random.nextFloat() * (session.height - 100) + 50
                onEvent?.invoke("respawn")
            }
        }
    }

    private fun finishGame() {
        session.finished = true
        val best = session.players.values.maxByOrNull { it.score }!!
        session.winner = best.id
        onEvent?.invoke("win")
        // ÉЛО
        if (session.mode == GameMode.LanCompetitive && session.eloEnabled) {
            val me = localPlayer
            val change = if (me.id == best.id) 25 else -10
            EloLeaderboard.update(session.localName, change)
        }
    }
}

private fun Weapon.toAmmo(): Int = when (id) {
    "awp", "g3sg1" -> 10
    "shotgun" -> 8
    "ak47", "m4", "augs" -> 30
    "smg" -> 35
    "pistol" -> 18
    else -> 999
}

private val maxAmmoMap = mapOf(
    "awp" to 10, "g3sg1" to 10, "shotgun" to 8, "ak47" to 30, "m4" to 30,
    "augs" to 30, "smg" to 35, "pistol" to 18, "knife" to 999)

private val Weapon.maxAmmo: Int get() = maxAmmoMap[id] ?: 30

/** ÉЛО (interne + synchronisé avec internet via MQTT). */
object EloLeaderboard {
    private var ratings = mutableMapOf<String, Int>()
    var onChange: ((List<Pair<String, Int>>) -> Unit)? = null

    fun update(name: String, change: Int) {
        ratings[name] = (ratings[name] ?: 1000) + change
        onChange?.invoke(top())
    }

    fun set(name: String, elo: Int) { ratings[name] = elo }
    fun get(name: String): Int = ratings[name] ?: 1000
    fun top(): List<Pair<String, Int>> = ratings.map { it.key to it.value }.sortedByDescending { it.second }
    fun clear() { ratings.clear() }
}
