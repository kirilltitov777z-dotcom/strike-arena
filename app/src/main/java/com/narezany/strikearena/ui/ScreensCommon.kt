package com.narezany.strikearena.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.narezany.strikearena.model.*

@Composable
fun SkinsScreen(
    onBack: () -> Unit,
    currentSkin: String,
    onSelect: (String) -> Unit,
    coins: Int,
    onAddCoins: () -> Unit,
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
            Text(text = "СКИНЫ И ПРЕДМЕТЫ", style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFFFF6B1A.toInt())))
            Text(text = "🪙 $coins", style = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFFF1C40F.toInt()), fontWeight = FontWeight.Bold))
        }
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(SKINS) { skin ->
                SkinRow(
                    skin = skin,
                    selected = skin.id == currentSkin,
                    owned = !skin.locked,
                    onSelect = { if (!skin.locked) onSelect(skin.id) else onBuy(skin) },
                    onBuy = { },
                    coins = coins,
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onBack, modifier = Modifier.width(200.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
            Text(text = "НАЗАД", color = Color.White)
        }
    }
}

@Composable
private fun SkinRow(
    skin: Skin,
    selected: Boolean,
    owned: Boolean,
    onSelect: () -> Unit,
    onBuy: () -> Unit,
    coins: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp)
                .border(if (selected) 3.dp else 0.dp, Color(0xFFFF6B1A.toInt()), shape = CircleShape)
                .background(if (selected) Color(0xFF161B26.toInt()) else Color(0xFF121721.toInt()), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = skinNameIcon(skin.id), style = MaterialTheme.typography.displaySmall.copy(fontSize = 30.sp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = skin.name, style = MaterialTheme.typography.bodyLarge.copy(color = Color.White, fontWeight = FontWeight.Bold))
            if (skin.locked) Text(text = "🔒 $coins", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFF1C40F.toInt())))
        }
        Spacer(modifier = Modifier.weight(1f))
        if (!owned) {
            Button(onClick = onBuy, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1C40F.toInt()))) {
                Text(text = "КУПИТЬ", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(onClick = onSelect, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1ABC9C.toInt()))) {
                Text(text = if (selected) "НАДЕТО" else "ОДЕТЬ", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun skinNameIcon(id: String): String = when (id) {
    "default" -> "👤"
    "ghost" -> "👻"
    "gold" -> "👑"
    "blood" -> "🩸"
    "neon" -> "⚡"
    "butterfly" -> "🦋"
    else -> "👤"
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var sens by remember { mutableFloatStateOf(1.2f) }
    var botDif by remember { mutableStateOf(BotDifficulty.Medium) }
    var hud by remember { mutableBooleanStateOf(true) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0F19.toInt())),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "НАСТРОЙКИ", style = MaterialTheme.typography.titleLarge.copy(color = Color(0xFFFF6B1A.toInt())))
        Spacer(modifier = Modifier.height(24.dp))
        settingRow("Чувствительность", "$sens", 0.5f, 2.5f, sens, { v -> sens = v })
        settingRow("Сложность ботов", botDif.name, null, null, null, null, options = BotDifficulty.values(), selected = botDif, onSelect = { botDif = it })
        CheckboxRow(
            checked = hud,
            label = "Показать HUD",
            onCheckedChange = { hud = it },
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onBack, modifier = Modifier.width(200.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161B26.toInt()))) {
            Text(text = "НАЗАД", color = Color.White)
        }
    }
}

@Composable
private fun settingRow(
    label: String, value: String,
    min: Float?, max: Float?,
    current: Float?,
    onChange: ((Float) -> Unit)?,
    options: Array<BotDifficulty>? = null,
    selected: BotDifficulty? = null,
    onSelect: ((BotDifficulty) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE8EAED.toInt())), modifier = Modifier.width(180.dp))
        if (options != null) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = {},
                modifier = Modifier.width(140.dp),
            ) {
                for (o in options) {
                    DropdownMenuItem(
                        text = { Text(text = o.name, color = Color.White) },
                        onClick = { onSelect?.invoke(o) },
                    )
                }
            }
            Text(text = selected?.name ?: "", style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFFF6B1A.toInt())))
        } else {
            Slider(
                value = current ?: 1f,
                onValueChange = { onChange?.invoke(it) },
                valueRange = (min ?: 0f)..(max ?: 1f),
            )
            Text(text = value, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFF6B1A.toInt())), modifier = Modifier.width(32.dp))
        }
    }
}

@Composable
private fun CheckboxRow(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE8EAED.toInt())))
    }
}
