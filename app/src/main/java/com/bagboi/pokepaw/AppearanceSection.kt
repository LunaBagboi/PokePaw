package com.bagboi.pokepaw

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement

@Composable
fun AppearanceSection(
    appearanceExpanded: Boolean,
    onAppearanceExpandedChange: () -> Unit,
    selectedBallTheme: BallTheme,
    onSelectedBallThemeChange: (BallTheme) -> Unit,
    ballIconBitmaps: BallIconBitmaps,
    selectedTint: Tint,
    onTintSelected: (Tint) -> Unit,
    selectedShader: ShaderOption,
    onSelectedShaderChange: (ShaderOption) -> Unit,
    shadeLevel: Int,
    onShadeLevelChange: (Int) -> Unit,
    selectedLcdSize: String,
    onSelectedLcdSizeChange: (String) -> Unit,
    colorizationEnabled: Boolean,
    onColorizationEnabledChange: (Boolean) -> Unit,
    controlWidth: Dp,
    dropdownBackground: Color
) {
    Text(
        text = (if (appearanceExpanded) "\u25BE " else "\u25B8 ") + "Appearance",
        color = Color(0xFFAAAAFF),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAppearanceExpandedChange() }
            .padding(vertical = 10.dp)
    )

    if (!appearanceExpanded) return

    var ballThemeMenuExpanded by remember { mutableStateOf(false) }
    var tintMenuExpanded by remember { mutableStateOf(false) }
    var shaderMenuExpanded by remember { mutableStateOf(false) }
    var lcdSizeMenuExpanded by remember { mutableStateOf(false) }

    val tintLabel = tintLabelFor(selectedTint)

    // Ball Theme row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Ball Theme",
            color = Color(0xFFB0B0C8),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .width(controlWidth)
                .background(Color(0xFF20203A), RoundedCornerShape(8.dp))
                .clickable { ballThemeMenuExpanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val iconBitmap = ballThemeIcon(
                    selectedBallTheme,
                    ballIconBitmaps.iconPokeballBitmap,
                    ballIconBitmaps.iconGreatballBitmap,
                    ballIconBitmaps.iconUltraballBitmap,
                    ballIconBitmaps.iconMasterballBitmap,
                    ballIconBitmaps.iconLevelballBitmap,
                    ballIconBitmaps.iconLoveballBitmap,
                    ballIconBitmaps.iconNetballBitmap,
                    ballIconBitmaps.iconPremierballBitmap,
                    ballIconBitmaps.iconQuickballBitmap,
                    ballIconBitmaps.iconSafariballBitmap,
                    ballIconBitmaps.iconBeastballBitmap,
                    ballIconBitmaps.iconLuxuryballBitmap
                )

                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = ballThemeLabel(selectedBallTheme),
                        color = Color(0xFFEFEFFF),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .weight(1f)
                    )
                } else {
                    Text(
                        text = ballThemeLabel(selectedBallTheme),
                        color = Color(0xFFEFEFFF),
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            DropdownMenu(
                expanded = ballThemeMenuExpanded,
                onDismissRequest = { ballThemeMenuExpanded = false },
                modifier = Modifier
                    .width(controlWidth + 32.dp)
                    .heightIn(max = 260.dp)
                    .background(dropdownBackground, RoundedCornerShape(8.dp))
            ) {
                val ballMenuScroll = rememberScrollState()

                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(ballMenuScroll)
                ) {
                    @Composable
                    fun ballThemeItem(theme: BallTheme) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val iconBitmap = ballThemeIcon(
                                        theme,
                                        ballIconBitmaps.iconPokeballBitmap,
                                        ballIconBitmaps.iconGreatballBitmap,
                                        ballIconBitmaps.iconUltraballBitmap,
                                        ballIconBitmaps.iconMasterballBitmap,
                                        ballIconBitmaps.iconLevelballBitmap,
                                        ballIconBitmaps.iconLoveballBitmap,
                                        ballIconBitmaps.iconNetballBitmap,
                                        ballIconBitmaps.iconPremierballBitmap,
                                        ballIconBitmaps.iconQuickballBitmap,
                                        ballIconBitmaps.iconSafariballBitmap,
                                        ballIconBitmaps.iconBeastballBitmap,
                                        ballIconBitmaps.iconLuxuryballBitmap
                                    )

                                    if (iconBitmap != null) {
                                        Image(
                                            bitmap = iconBitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Text(
                                            text = ballThemeLabel(theme),
                                            color = Color(0xFFEFEFFF),
                                            fontSize = 13.sp,
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                        )
                                    } else {
                                        Text(
                                            text = ballThemeLabel(theme),
                                            color = Color(0xFFEFEFFF),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectedBallThemeChange(theme)
                                ballThemeMenuExpanded = false
                            }
                        )
                    }

                    ballThemeItem(BallTheme.None)
                    ballThemeItem(BallTheme.PokeBall)
                    ballThemeItem(BallTheme.GreatBall)
                    ballThemeItem(BallTheme.UltraBall)
                    ballThemeItem(BallTheme.MasterBall)
                    ballThemeItem(BallTheme.LevelBall)
                    ballThemeItem(BallTheme.LoveBall)
                    ballThemeItem(BallTheme.NetBall)
                    ballThemeItem(BallTheme.PremierBall)
                    ballThemeItem(BallTheme.QuickBall)
                    ballThemeItem(BallTheme.SafariBall)
                    ballThemeItem(BallTheme.BeastBall)
                    ballThemeItem(BallTheme.LuxuryBall)
                }
            }
        }
    }

    // Colorization row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Colorization",
            color = Color(0xFFB0B0C8),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .width(controlWidth)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = colorizationEnabled,
                    onCheckedChange = { checked -> onColorizationEnabledChange(checked) }
                )
            }
        }
    }

    // Tint row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Tint",
            color = Color(0xFFB0B0C8),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .width(controlWidth)
                .background(Color(0xFF20203A), RoundedCornerShape(8.dp))
                .clickable { tintMenuExpanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = tintLabel,
                    color = Color(0xFFEFEFFF),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )

                Row {
                    tintColorsFor(selectedTint).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(color, RoundedCornerShape(2.dp))
                                .padding(end = 2.dp)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = tintMenuExpanded,
                onDismissRequest = { tintMenuExpanded = false },
                modifier = Modifier
                    .background(dropdownBackground, RoundedCornerShape(8.dp))
            ) {
                @Composable
                fun tintItem(label: String, tint: Tint) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = label,
                                    color = Color(0xFFEFEFFF),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                Row {
                                    tintColorsFor(tint).forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(color, RoundedCornerShape(2.dp))
                                                .padding(end = 2.dp)
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            onTintSelected(tint)
                            tintMenuExpanded = false
                        }
                    )
                }

                tintItem("None", Tint.None)
                tintItem("SGB", Tint.SGB)
                tintItem("Green", Tint.Green)
                tintItem("Red", Tint.Red)
                tintItem("Blue", Tint.Blue)
            }
        }
    }

    // Shade row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Shade",
            color = Color(0xFFB0B0C8),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .width(controlWidth)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..10).forEach { level ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .padding(horizontal = 1.dp)
                            .background(
                                if (level <= shadeLevel) Color(0xFFEFEFFF) else Color(0xFF404060),
                                RoundedCornerShape(3.dp)
                            )
                            .clickable { onShadeLevelChange(level) }
                    )
                }
            }
        }
    }

    // Shader row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Shader",
            color = Color(0xFFB0B0C8),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .width(controlWidth)
                .background(Color(0xFF20203A), RoundedCornerShape(8.dp))
                .clickable { shaderMenuExpanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when (selectedShader) {
                        ShaderOption.None -> "None"
                        ShaderOption.Xbrz2x -> "xBRZ 2x"
                        ShaderOption.Xbrz3x -> "xBRZ 3x"
                        ShaderOption.Xbrz4x -> "xBRZ 4x"
                    },
                    color = Color(0xFFEFEFFF),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            DropdownMenu(
                expanded = shaderMenuExpanded,
                onDismissRequest = { shaderMenuExpanded = false },
                modifier = Modifier
                    .background(dropdownBackground, RoundedCornerShape(8.dp))
            ) {
                @Composable
                fun shaderItem(label: String, option: ShaderOption) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
                                color = Color(0xFFEFEFFF),
                                fontSize = 13.sp
                            )
                        },
                        onClick = {
                            onSelectedShaderChange(option)
                            shaderMenuExpanded = false
                        }
                    )
                }

                shaderItem("None", ShaderOption.None)
                shaderItem("xBRZ 2x", ShaderOption.Xbrz2x)
                shaderItem("xBRZ 3x", ShaderOption.Xbrz3x)
                shaderItem("xBRZ 4x", ShaderOption.Xbrz4x)
            }
        }
    }

    // LCD Size row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "LCD Size",
            color = Color(0xFFB0B0C8),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .width(controlWidth)
                .background(Color(0xFF20203A), RoundedCornerShape(8.dp))
                .clickable { lcdSizeMenuExpanded = true }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selectedLcdSize,
                    color = Color(0xFFEFEFFF),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            DropdownMenu(
                expanded = lcdSizeMenuExpanded,
                onDismissRequest = { lcdSizeMenuExpanded = false },
                modifier = Modifier
                    .background(dropdownBackground, RoundedCornerShape(8.dp))
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Small",
                            color = Color(0xFFEFEFFF),
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onSelectedLcdSizeChange("Small")
                        lcdSizeMenuExpanded = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Large",
                            color = Color(0xFFEFEFFF),
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onSelectedLcdSizeChange("Large")
                        lcdSizeMenuExpanded = false
                    }
                )
            }
        }
    }
}
