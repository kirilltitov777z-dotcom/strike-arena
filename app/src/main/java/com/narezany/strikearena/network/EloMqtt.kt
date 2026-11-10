package com.narezany.strikearena.network

import android.os.Handler
import org.eclipse.paho.client.mqttv3.*
import com.narezany.strikearena.model.*
import java.util.UUID

/** Таблица ЭЛО и лидерборд через публичный MQTT-брокер (интернет). */
class EloMqtt(private val uiHandler: Handler) : MqttCallback {
    private val clientId = "strike_" + UUID.randomUUID().toString().substring(0, 8)
    private lateinit var client: MqttAsyncClient
    private val broker = "tcp://broker.emqx.io:1883"
    private val topicLeader = "strikearena/leaderboard"

    var onLeaderboard: ((List<EloEntry>) -> Unit)? = null
    var onStatus: ((String) -> Unit)? = null

    private var connected = false

    data class EloEntry(val name: String, val elo: Int, val ts: Long)

    fun connect() {
        Thread {
            try {
                client = MqttAsyncClient(broker, clientId)
                client.setCallback(this)
                val opts = MqttConnectOptions().apply {
                    isCleanSession = true
                    setAutomaticReconnect(true)
                }
                client.connect(opts).waitForCompletion(10_000)
                connected = true
                uiHandler.post { onStatus?.invoke("ЭЛО: онлайн (интернет)") }
            } catch (e: Exception) {
                connected = false
                uiHandler.post {
                    onStatus?.invoke("ЭЛО офлайн (нет интернета): ${e.message?.take(60)}")
                }
            }
        }.start()
    }

    fun publishElo(name: String, elo: Int) {
        if (!connected) return
        try {
            val json = """{"name":"$name","elo":$elo,"ts":${System.currentTimeMillis()}}"""
            client.publish(topicLeader, json.toByteArray(), 0, false)
        } catch (_: Exception) {}
    }

    fun disconnect() {
        try { client.disconnect().waitForCompletion(3000) } catch (_: Exception) {}
        connected = false
    }

    override fun connectionLost(cause: Throwable?) {
        connected = false
        uiHandler.post { onStatus?.invoke("ЭЛО: соединение потеряно") }
    }

    override fun messageArrived(topic: String, message: MqttMessage) {
        val payload = String(message.payload)
        try {
            val entry = parseElo(payload)
            if (entry != null) {
                uiHandler.post { onLeaderboard?.invoke(listOf(entry)) }
            }
        } catch (_: Exception) {}
    }

    private fun parseElo(json: String): EloEntry? {
        val name = between(json, "\"name\":\"", "\"")
        if (name == null) return null
        val elo = between(json, "\"elo\":", ",")?.toIntOrNull() ?: 1000
        val ts = between(json, "\"ts\":", "}")?.toLongOrNull() ?: System.currentTimeMillis()
        return EloEntry(name, elo, ts)
    }

    private fun between(json: String, start: String, end: String): String? {
        val s = json.indexOf(start)
        if (s < 0) return null
        val from = s + start.length
        val e = json.indexOf(end, from)
        if (e < 0) return null
        return json.substring(from, e)
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {}
}
