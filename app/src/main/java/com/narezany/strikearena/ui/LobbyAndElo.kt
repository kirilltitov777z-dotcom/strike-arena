package com.narezany.strikearena.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.FontWeight
import androidx.compose.ui.unit.dp
import com.narezany.strikearena.model.*
import java.net.NetworkInterface
import java.net.SocketException
import java.util.*

@Composable
fun LobbyScreen(
    onBack: () -> Unit,
    onStartHost: () -> Unit,
    onJoin: (String) -> Unit,
    status: String,
    myIp: String,
    connected: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F19.toInt())),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "ЛАН МУЛЬТИПЛЕЕР", style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFFFF6B1A.toInt())))
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Ваш IP: ", style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF9AA5B1.toInt())))
            Text(text = myIp, style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFF1C40F.toInt()), fontWeight = FontWeight.Bold))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = status, style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF9AA5B1.toInt())))
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.width(520.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = onStartHost, modifier = Modifier.weight(1f), enabled = !connected,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                Text(text = "СОЗДАТЬ ЛОББИ", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.width(520.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var ip by remember { mutableStateOf("") }
            OutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = { Text(text = "IP хоста", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9AA5B1.toInt()))) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    unfocusedContainerColor = Color(0xFF121721.toInt()),
                    focusedContainerColor = Color(0xFF161B26.toInt()),
                ),
            )
            Button(onClick = { if (ip.isNotEmpty()) onJoin(ip) }, enabled = !connected && ip.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                Text(text = "ПОДКЛЮЧИТЬСЯ", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
        Button(onClick = onBack, modifier = Modifier.width(200.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
            Text(text = "НАЗАД", color = Color.White)
        }
    }
}

fun getLocalIp(): String {
    try {
        val interfaces = NetworkInterface.networkInterfaces
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            val addrs = ni.inetAddresses
            while (addrs.hasMoreElements()) {
                val addr = addrs.nextElement()
                if (!addr.isLoopbackAddress && addr.hostAddress.contains(".")) return addr.hostAddress
            }
        }
    } catch (_: SocketException) {}
    return "127.0.0.1"
}

@Composable
fun EloScreen(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    entries: List<Pair<String, Int>>,
    yourName: String,
    yourElo: Int,
    status: String,
    onConnect: () -> Unit,
    connected: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F19.toInt())),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "ТАБЛИЦА ЭЛО", style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFFFF6B1A.toInt())))
            Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                Text(text = "ОБНОВИТЬ", color = Color.White)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = status, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9AA5B1.toInt())))
        Spacer(modifier = Modifier.height(12.dp))
        if (!connected) {
            Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                Text(text = "ПОДКЛЮЧИТЬСЯ К ИНТЕРНЕТ-ЭЛО", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            itemsIndexed(entries) { index, pair ->
                EloRow(
                    rank = index + 1,
                    name = pair.first,
                    elo = pair.second,
                    isYou = pair.first == yourName,
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text = "ВАШ ЭЛО: $yourElo  ($yourName)", style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFF1C40F.toInt()), fontWeight = FontWeight.Bold))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.width(200.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
            Text(text = "НАЗАД", color = Color.White)
        }
    }
}

@Composable
private fun EloRow(rank: Int, name: String, elo: Int, isYou: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "$rank.", style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF9AA5B1.toInt())), modifier = Modifier.width(32.dp))
        Box(
            modifier = Modifier.size(32.dp)
                .background(if (isYou) Color(0xFFFF6B1A.toInt()) else Color(0xFF121721.toInt()))
                .wrapContentSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = name.first().uppercase(), color = Color.Black, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge.copy(color = if (isYou) Color(0xFFFF6B1A.toInt()) else Color.White, fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.weight(1f))
        Text(text = elo.toString(), style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFF1C40F.toInt()), fontWeight = FontWeight.Bold))
    }
}
