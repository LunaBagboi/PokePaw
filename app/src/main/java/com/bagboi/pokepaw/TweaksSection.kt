package com.bagboi.pokepaw

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TweaksSection(
    cheatsExpanded: Boolean,
    onCheatsExpandedChange: () -> Unit,
    debugExpanded: Boolean,
    onDebugExpandedChange: () -> Unit,
    disableSleepCheatEnabled: Boolean,
    onDisableSleepCheatChange: (Boolean) -> Unit,
    onAdjustWatts: (Int) -> Unit,
    irEnabled: Boolean,
    onIrEnabledChange: (Boolean) -> Unit,
    irHost: String,
    onIrHostChange: (String) -> Unit,
    irPortText: String,
    onIrPortTextChange: (String) -> Unit,
    showDebugOverlay: Boolean,
    onShowDebugOverlayChange: (Boolean) -> Unit,
    controlWidth: Dp
) {
    var showWattsDialog by remember { mutableStateOf(false) }
    var wattsDeltaText by remember { mutableStateOf("0") }

    Text(
        text = (if (cheatsExpanded) "\u25BE " else "\u25B8 ") + "Tweaks",
        color = Color(0xFFAAAAFF),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheatsExpandedChange() }
            .padding(vertical = 10.dp)
    )

    if (cheatsExpanded) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Cheats",
                color = Color(0xFFB0B0C8),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                    .background(Color(0xFF20203A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Always Awake",
                                color = Color(0xFFB0B0C8),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Prevents the emulator and LCD from going to sleep.",
                                color = Color(0xFF8080A0),
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(Color(0xFF15152A), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF505070), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Checkbox(
                                    checked = disableSleepCheatEnabled,
                                    onCheckedChange = onDisableSleepCheatChange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(0xFF15152A), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF505070), RoundedCornerShape(6.dp))
                                    .clickable { },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "⚙",
                                    color = Color(0xFFB0B0C8),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.padding(top = 6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Adjust Watts",
                                color = Color(0xFFB0B0C8),
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Add or remove watts by a custom amount.",
                                color = Color(0xFF8080A0),
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                wattsDeltaText = "0"
                                showWattsDialog = true
                            },
                            modifier = Modifier
                                .width(controlWidth)
                                .padding(start = 4.dp)
                        ) {
                            Text("Add", fontSize = 12.sp)
                        }
                    }
                }
            }

            Text(
                text = (if (debugExpanded) "\u25BE " else "\u25B8 ") + " Debug",
                color = Color(0xFFB0B0C8),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDebugExpandedChange() }
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
            )

            if (debugExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .background(Color(0xFF20203A), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Show overlay debug HUD",
                                    color = Color(0xFFB0B0C8),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Displays watts/route stats at the top of the app.",
                                    color = Color(0xFF8080A0),
                                    fontSize = 11.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(Color(0xFF15152A), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF505070), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Checkbox(
                                    checked = showDebugOverlay,
                                    onCheckedChange = onShowDebugOverlayChange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "IR Host",
                        color = Color(0xFFB0B0C8),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )

                    TextField(
                        value = irHost,
                        onValueChange = onIrHostChange,
                        modifier = Modifier.width(controlWidth)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, top = 4.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "IR Port",
                        color = Color(0xFFB0B0C8),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )

                    TextField(
                        value = irPortText,
                        onValueChange = onIrPortTextChange,
                        modifier = Modifier.width(controlWidth)
                    )
                }
            }
        }
    }

    if (showWattsDialog) {
        AlertDialog(
            onDismissRequest = { showWattsDialog = false },
            title = {
                Text(
                    text = "Adjust Watts",
                    color = Color(0xFFF5F5F7),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a positive or negative number. Result is clamped between 0 and 9999.",
                        color = Color(0xFFB0B0C8),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.padding(top = 8.dp))

                    TextField(
                        value = wattsDeltaText,
                        onValueChange = { wattsDeltaText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val delta = wattsDeltaText.toIntOrNull() ?: 0
                        onAdjustWatts(delta)
                        showWattsDialog = false
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                Button(onClick = { showWattsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
