package com.bagboi.pokepaw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures

@Composable
fun SoundSection(
    soundExpanded: Boolean,
    onSoundExpandedChange: () -> Unit,
    softChirpEnabled: Boolean,
    onSoftChirpEnabledChange: (Boolean) -> Unit,
    volumeLevel: Int,
    onVolumeLevelChange: (Int) -> Unit,
    hapticsStrength: Int,
    onHapticsStrengthChange: (Int) -> Unit,
    controlWidth: Dp
) {
    Text(
        text = (if (soundExpanded) "\u25BE " else "\u25B8 ") + "Sound",
        color = Color(0xFFAAAAFF),
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSoundExpandedChange() }
            .padding(vertical = 10.dp)
    )

    if (soundExpanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Soft Chirp",
                color = Color(0xFFB0B0C8),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )

            Checkbox(
                checked = softChirpEnabled,
                onCheckedChange = onSoftChirpEnabledChange
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Volume",
                color = Color(0xFFB0B0C8),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier = Modifier
                    .width(controlWidth)
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            val widthPx = size.width.toFloat().coerceAtLeast(1f)
                            val x = change.position.x.coerceIn(0f, widthPx)
                            val fraction = x / widthPx
                            val newLevel = (fraction * 10).toInt().coerceIn(0, 9) + 1
                            onVolumeLevelChange(newLevel)
                            change.consume()
                        }
                    }
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
                                    if (level <= volumeLevel) Color(0xFFEFEFFF) else Color(0xFF404060),
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable { onVolumeLevelChange(level) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Haptics Strength",
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
                    (1..3).forEach { level ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .padding(horizontal = 2.dp)
                                .background(
                                    if (level <= hapticsStrength) Color(0xFFEFEFFF) else Color(0xFF404060),
                                    RoundedCornerShape(3.dp)
                                )
                                .clickable { onHapticsStrengthChange(level) }
                        )
                    }
                }
            }
        }
    }
}
