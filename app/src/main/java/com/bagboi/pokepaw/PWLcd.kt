package com.bagboi.pokepaw

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun PWLcd(
    canvasBitmap: Bitmap?,
    lcdBackground: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size((96 * 2.5 + 32 + 16).dp, (64 * 2.5 + 32 + 16).dp)
                .background(lcdBackground, shape = RoundedCornerShape(16.dp))
                .align(Alignment.Center)
                .border(
                    width = 8.dp,
                    color = Color(0xFF1B1B1B),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            canvasBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Pokewalker Display",
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None
                )
            }
        }
    }
}
