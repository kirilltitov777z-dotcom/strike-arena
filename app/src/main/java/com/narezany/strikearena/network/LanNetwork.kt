package com.narezany.strikearena.network

import android.os.Handler
import com.narezany.strikearena.model.LanMsg
import com.narezany.strikearena.model.LanMsg.*
import java.io.*
import java.net.*
import java.util.*

class LanNetwork(private val uiHandler: Handler) : Closeable {
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private var input: ObjectInputStream? = null
    private var out: ObjectOutputStream? = null
    private val clients = mutableMapOf<String, ClientLink>()
    private val clientOuts = mutableMapOf<String, ObjectOutputStream>()

    var onMessage: ((LanMsg) -> Unit)? = null
    var onClientConnected: ((String) -> Unit)? = null
    var onClientDisconnected: ((String) -> Unit)? = null
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var isHost: Boolean = false
        private set

    data class ClientLink(val socket: Socket, val out: ObjectOutputStream, val input: ObjectInputStream)

    fun startHost(port: Int = 27015) {
        isHost = true
        val srv = ServerSocket(port).apply { setReuseAddress(true) }
        server = srv
        Thread { acceptLoop(srv) }.start()
    }

    private fun acceptLoop(srv: ServerSocket) {
        while (isHost && !srv.isClosed) {
            try {
                val s = srv.accept()
                s.setTcpNoDelay(true)
                val oos = ObjectOutputStream(s.outputStream).apply { flush() }
                val ois = ObjectInputStream(s.inputStream)
                Thread { clientReadLoop("peer", s, ois, oos) }.start()
            } catch (_: Exception) { break }
        }
    }

    fun connect(host: String, port: Int = 27015, myId: String,
                name: String, skin: String, weapon: String) {
        isHost = false
        Thread {
            var ok = false
            try {
                val sock = Socket(host, port)
                sock.setTcpNoDelay(true)
                val oos = ObjectOutputStream(sock.outputStream).apply { flush() }
                val ois = ObjectInputStream(sock.inputStream)
                socket = sock
                out = oos
                input = ois
                ok = true
                oos.writeObject(LanMsg.Join(myId, name, skin, weapon))
                oos.flush()
                uiHandler.post { onConnected?.invoke() }
                clientReadLoop(myId, sock, ois, oos)
            } catch (e: Exception) {
                if (!ok) uiHandler.post { onDisconnected?.invoke() }
            }
        }.start()
    }

    private fun clientReadLoop(myId: String, s: Socket, ois: ObjectInputStream, oos: ObjectOutputStream) {
        try {
            while (!s.isClosed) {
                val msg = ois.readObject()
                if (msg is LanMsg) {
                    when (msg) {
                        is Join -> {
                            if (isHost) {
                                val id = msg.id
                                clients[id] = ClientLink(s, oos, ois)
                                clientOuts[id] = oos
                                uiHandler.post { onClientConnected?.invoke(id) }
                                broadcast(Join(msg.id, msg.name, msg.skin, msg.weapon), exclude = id)
                                uiHandler.post { onMessage?.invoke(msg) }
                            } else {
                                uiHandler.post { onMessage?.invoke(msg) }
                            }
                        }
                        else -> uiHandler.post { onMessage?.invoke(msg) }
                    }
                }
            }
        } catch (_: Exception) {} finally {
            try { s.close() } catch (_: Exception) {}
            uiHandler.post {
                onClientDisconnected?.invoke(myId)
                onDisconnected?.invoke()
            }
        }
    }

    fun send(msg: LanMsg, myId: String) {
        if (isHost) {
            broadcast(msg, exclude = myId)
            return
        }
        try { out?.writeObject(msg); out?.flush() } catch (_: Exception) {}
    }

    fun broadcast(msg: LanMsg, exclude: String? = null) {
        clientOuts.entries.filter { it.key != exclude }.forEach { (_, oos) ->
            try { oos.writeObject(msg); oos.flush() } catch (_: Exception) {}
        }
    }

    fun disconnect() {
        isHost = false
        try { server?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { input?.close() } catch (_: Exception) {}
        try { out?.close() } catch (_: Exception) {}
        clients.values.forEach { try { it.socket.close() } catch (_: Exception) {} }
        clients.clear()
        clientOuts.clear()
        uiHandler.post { onDisconnected?.invoke() }
    }

    override fun close() = disconnect()
}
