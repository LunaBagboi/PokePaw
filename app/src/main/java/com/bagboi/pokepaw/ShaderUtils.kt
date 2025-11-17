package com.bagboi.pokepaw

import android.content.SharedPreferences
import android.graphics.Bitmap
import io.github.stanio.xbrz.Xbrz

enum class ShaderOption {
    None,
    Xbrz2x,
    Xbrz3x,
    Xbrz4x
}

fun getCurrentShaderOption(preferences: SharedPreferences): ShaderOption {
    val raw = preferences.getString("shader_option", ShaderOption.None.name)
    return runCatching { ShaderOption.valueOf(raw ?: ShaderOption.None.name) }
        .getOrElse { ShaderOption.None }
}

fun applyShaderOption(bitmap: Bitmap, option: ShaderOption): Bitmap {
    return when (option) {
        ShaderOption.None -> bitmap
        ShaderOption.Xbrz2x -> scaleBitmapXbrz(bitmap, 2)
        ShaderOption.Xbrz3x -> scaleBitmapXbrz(bitmap, 3)
        ShaderOption.Xbrz4x -> scaleBitmapXbrz(bitmap, 4)
    }
}

fun scaleBitmapXbrz(source: Bitmap, scale: Int): Bitmap {
    if (scale <= 1) return source

    val srcWidth = source.width
    val srcHeight = source.height
    val dstWidth = srcWidth * scale
    val dstHeight = srcHeight * scale

    val srcPixels = IntArray(srcWidth * srcHeight)
    source.getPixels(srcPixels, 0, srcWidth, 0, 0, srcWidth, srcHeight)

    val hasAlpha = true
    val dstPixels = Xbrz.scaleImage(scale, hasAlpha, srcPixels, null, srcWidth, srcHeight)

    val scaled = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
    scaled.setPixels(dstPixels, 0, dstWidth, 0, 0, dstWidth, dstHeight)
    return scaled
}
