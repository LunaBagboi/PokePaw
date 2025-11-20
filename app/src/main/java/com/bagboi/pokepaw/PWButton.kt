package com.bagboi.pokepaw

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.halfheart.pocketwalkerlib.PocketWalkerNative
import kotlinx.coroutines.delay

@Composable
fun PWButton(
    pokeWalker: PocketWalkerNative?,
    button: Int,
    size: Int = 32,
    top: Int = 0,
    bottom: Int = 0,
    start: Int = 0,
    end: Int = 0,
    modifier: Modifier = Modifier
) {
    var isPressed = remember { mutableStateOf(false) }

    val buttonOutlineColor = Color(0xFFCFCFCF)
    val buttonColor = Color(0xFFF3F3F3)
    val pressedButtonColor = Color(0xFFDFDFDF)

    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .padding(top = top.dp, bottom = bottom.dp, start = start.dp, end = end.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed.value = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        pokeWalker?.press(button)
                        tryAwaitRelease()
                        isPressed.value = false
                        pokeWalker?.release(button)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(if (isPressed.value) pressedButtonColor else buttonColor, CircleShape)
                .border(BorderStroke(2.dp, buttonOutlineColor), CircleShape)
                .align(Alignment.Center)
        )
    }
}
