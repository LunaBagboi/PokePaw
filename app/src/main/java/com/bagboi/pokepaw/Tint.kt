package com.bagboi.pokepaw

import androidx.compose.ui.graphics.Color

enum class Tint {
    None,
    SGB,
    Green,
    Red,
    Blue
}

fun tintPalette(tint: Tint): IntArray = when (tint) {
    Tint.None -> intArrayOf(
        0xCCCCCC,
        0x999999,
        0x666666,
        0x333333
    )
    Tint.SGB -> intArrayOf(
        0xFFEFFF,
        0xF7B58C,
        0x84739C,
        0x181101
    )
    Tint.Green -> intArrayOf(
        0xE0F8D0,
        0x88C070,
        0x346856,
        0x081820
    )
    Tint.Red -> intArrayOf(
        0xFFF4F4,
        0xF2B2B2,
        0xD46A6A,
        0x3C1212
    )
    Tint.Blue -> intArrayOf(
        0xF5F5F7,
        0x8787A1,
        0x58588A,
        0x1E1E29
    )
}

fun tintLcdBackground(tint: Tint): Color = when (tint) {
    Tint.None -> Color(0xFFCCCCCC)
    Tint.SGB -> Color(0xFFFFEFFF)
    Tint.Green -> Color(0xFFE0F8D0)
    Tint.Red -> Color(0xFFFFF4F4)
    Tint.Blue -> Color(0xFFF5F5F7)
}

fun tintSidebarBackground(tint: Tint): Color = when (tint) {
    Tint.None -> Color(0xFF111119)
    Tint.SGB -> Color(0xFF181101)
    Tint.Green -> Color(0xFF081820)
    Tint.Red -> Color(0xFF3C1212)
    Tint.Blue -> Color(0xFF1E1E29)
}

fun tintLabelFor(tint: Tint): String = when (tint) {
    Tint.None -> "None"
    Tint.SGB -> "SGB"
    Tint.Green -> "Green"
    Tint.Red -> "Red"
    Tint.Blue -> "Blue"
}

fun tintColorsFor(tint: Tint): List<Color> = when (tint) {
    Tint.None -> listOf(
        Color(0xFFCCCCCC),
        Color(0xFF999999),
        Color(0xFF666666),
        Color(0xFF333333)
    )
    Tint.SGB -> listOf(
        Color(0xFFFFEFFF),
        Color(0xFFF7B58C),
        Color(0xFF84739C),
        Color(0xFF181101)
    )
    Tint.Green -> listOf(
        Color(0xFFE0F8D0),
        Color(0xFF88C070),
        Color(0xFF346856),
        Color(0xFF081820)
    )
    Tint.Red -> listOf(
        Color(0xFFFFF4F4),
        Color(0xFFF2B2B2),
        Color(0xFFD46A6A),
        Color(0xFF3C1212)
    )
    Tint.Blue -> listOf(
        Color(0xFFF5F5F7),
        Color(0xFF8787A1),
        Color(0xFF58588A),
        Color(0xFF1E1E29)
    )
}
