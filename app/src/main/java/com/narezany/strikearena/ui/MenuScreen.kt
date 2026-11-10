package com.narezany.strikearena.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narezany.strikearena.model.*

@Composable
fun MenuScreen(
    onPlay: () -> Unit,
    onSkins: () -> Unit,
    onSettings: () -> Unit,
    onElo: () -> Unit,
    onExit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F19.toInt())),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "STRIKE ARENA",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 44.sp, fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFFF6B1A.toInt())),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Меню • Скины • Нож • Каллаш • AWP • LAN • ЭЛО",
            style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF9AA5B1.toInt())),
        )
        Spacer(modifier = Modifier.height(32.dp))

        val buttons = listOf(
            "ИГРАТЬ" to { onPlay() },
            "СКИНЫ" to { onSkins() },
            "НАСТРОЙКИ" to { onSettings() },
            "ЭЛО / ЛИДЕРЫ" to { onElo() },
            "ВЫХОД" to { onExit() },
        )
        for ((label, action) in buttons) {
            Button(
                onClick = action,
                modifier = Modifier.width(260.dp).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt())),
            ) {
                Text(text = label, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun PlaySubmenu(onBack: () -> Unit, onBot: () -> Unit, onLan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F19.toInt())),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "ВЫБЕРИТЕ РЕЖИМ", style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFFFF6B1A.toInt())))
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.width(560.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Button(onClick = onBot, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                Text(text = "ПРОТИВ БОТА", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Button(onClick = onLan, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
                Text(text = "ПРОТИВ ЛЮДЕЙ (LAN)", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack, modifier = Modifier.width(200.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
            Text(text = "НАЗАД", color = Color.White)
        }
    }
}
