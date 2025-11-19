package com.bagboi.pokepaw

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

enum class BallTheme {
    None,
    PokeBall,
    GreatBall,
    UltraBall,
    MasterBall,
    LevelBall,
    LoveBall,
    NetBall,
    PremierBall,
    QuickBall,
    SafariBall,
    BeastBall,
    LuxuryBall
}

data class BallBackgroundBitmaps(
    val pokeballBitmap: Bitmap?,
    val greatballBitmap: Bitmap?,
    val ultraballBitmap: Bitmap?,
    val masterballBitmap: Bitmap?,
    val levelballBitmap: Bitmap?,
    val premierballBitmap: Bitmap?,
    val quickballBitmap: Bitmap?,
    val safariballBitmap: Bitmap?,
    val pokepawBitmap: Bitmap?,
    val beastballBitmap: Bitmap?,
    val luxuryballBitmap: Bitmap?,
    val loveballBitmap: Bitmap?,
    val netballBitmap: Bitmap?
)

data class BallIconBitmaps(
    val iconPokeballBitmap: Bitmap?,
    val iconGreatballBitmap: Bitmap?,
    val iconUltraballBitmap: Bitmap?,
    val iconMasterballBitmap: Bitmap?,
    val iconLevelballBitmap: Bitmap?,
    val iconLoveballBitmap: Bitmap?,
    val iconNetballBitmap: Bitmap?,
    val iconPremierballBitmap: Bitmap?,
    val iconQuickballBitmap: Bitmap?,
    val iconSafariballBitmap: Bitmap?,
    val iconBeastballBitmap: Bitmap?,
    val iconLuxuryballBitmap: Bitmap?,
    val iconPokepawBitmap: Bitmap?
)

@Composable
fun rememberBallBackgroundBitmaps(): BallBackgroundBitmaps {
    val context = LocalContext.current

    return remember {
        BallBackgroundBitmaps(
            pokeballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/pokeball.png"))
            }.getOrNull(),
            greatballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/greatball.png"))
            }.getOrNull(),
            ultraballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/ultraball.png"))
            }.getOrNull(),
            masterballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/masterball.png"))
            }.getOrNull(),
            levelballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/levelball.png"))
            }.getOrNull(),
            premierballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/premierball.png"))
            }.getOrNull(),
            quickballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/quickball.png"))
            }.getOrNull(),
            safariballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/safariball.png"))
            }.getOrNull(),
            pokepawBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/pokepaw.png"))
            }.getOrNull(),
            beastballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/beastball.png"))
            }.getOrNull(),
            luxuryballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/luxuryball.png"))
            }.getOrNull(),
            loveballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/loveball.png"))
            }.getOrNull(),
            netballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/netball.png"))
            }.getOrNull()
        )
    }
}

@Composable
fun rememberBallIconBitmaps(): BallIconBitmaps {
    val context = LocalContext.current

    return remember {
        BallIconBitmaps(
            iconPokeballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-pokeball.png"))
            }.getOrNull(),
            iconGreatballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-greatball.png"))
            }.getOrNull(),
            iconUltraballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-ultraball.png"))
            }.getOrNull(),
            iconMasterballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-masterball.png"))
            }.getOrNull(),
            iconLevelballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-levelball.png"))
            }.getOrNull(),
            iconLoveballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-loveball.png"))
            }.getOrNull(),
            iconNetballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-netball.png"))
            }.getOrNull(),
            iconPremierballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-premierball.png"))
            }.getOrNull(),
            iconQuickballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-quickball.png"))
            }.getOrNull(),
            iconSafariballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-safariball.png"))
            }.getOrNull(),
            iconBeastballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-beastball.png"))
            }.getOrNull(),
            iconLuxuryballBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-luxuryball.png"))
            }.getOrNull(),
            iconPokepawBitmap = runCatching {
                BitmapFactory.decodeStream(context.assets.open("background-assets/icon-pokepaw.png"))
            }.getOrNull()
        )
    }
}

@Composable
fun BallThemeBackground(
    selectedBallTheme: BallTheme,
    bitmaps: BallBackgroundBitmaps,
    modifier: Modifier = Modifier
) {
    if (selectedBallTheme == BallTheme.None) return

    val defaultTop = Color(0xFFD32329)
    val defaultBottom = Color(0xffdadade)

    val topColor = when (selectedBallTheme) {
        BallTheme.UltraBall -> Color(0xFF191A1B)
        BallTheme.SafariBall -> Color(0xFF76BD25)
        BallTheme.QuickBall -> Color(0xFF4DA2CA)
        BallTheme.PremierBall -> Color(0xFFDBDBDF)
        BallTheme.MasterBall -> Color(0xFF6700C0)
        BallTheme.LevelBall -> Color(0xFF161616)
        BallTheme.GreatBall -> Color(0xFF007ED7)
        BallTheme.BeastBall -> Color(0xFF203699)
        BallTheme.LuxuryBall -> Color(0xFF161616)
        BallTheme.LoveBall -> Color(0xFFEC65CC)
        BallTheme.NetBall -> Color(0xFF00A5A7)
        else -> defaultTop
    }

    val bottomColor = when (selectedBallTheme) {
        BallTheme.BeastBall -> Color(0xFF203699)
        BallTheme.LuxuryBall -> Color(0xFF161616)
        BallTheme.QuickBall -> Color(0xFF4DA2CA)
        else -> defaultBottom
    }

    val bgBitmap: Bitmap? = when (selectedBallTheme) {
        BallTheme.PokeBall -> bitmaps.pokeballBitmap
        BallTheme.GreatBall -> bitmaps.greatballBitmap
        BallTheme.UltraBall -> bitmaps.ultraballBitmap
        BallTheme.MasterBall -> bitmaps.masterballBitmap
        BallTheme.LevelBall -> bitmaps.levelballBitmap
        BallTheme.PremierBall -> bitmaps.premierballBitmap
        BallTheme.QuickBall -> bitmaps.quickballBitmap
        BallTheme.SafariBall -> bitmaps.safariballBitmap
        BallTheme.BeastBall -> bitmaps.beastballBitmap
        BallTheme.LuxuryBall -> bitmaps.luxuryballBitmap
        BallTheme.LoveBall -> bitmaps.loveballBitmap
        BallTheme.NetBall -> bitmaps.netballBitmap
        else -> null
    }

    Box(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.TopCenter)
                .background(topColor)
        )

        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomCenter)
                .background(bottomColor)
        )

        bgBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low
            )
        }
    }
}

fun ballThemeLabel(theme: BallTheme): String {
    return when (theme) {
        BallTheme.None -> "None"
        BallTheme.PokeBall -> "Poké Ball"
        BallTheme.GreatBall -> "Great Ball"
        BallTheme.UltraBall -> "Ultra Ball"
        BallTheme.MasterBall -> "Master Ball"
        BallTheme.LevelBall -> "Level Ball"
        BallTheme.LoveBall -> "Love Ball"
        BallTheme.NetBall -> "Net Ball"
        BallTheme.PremierBall -> "Premier Ball"
        BallTheme.QuickBall -> "Quick Ball"
        BallTheme.SafariBall -> "Safari Ball"
        BallTheme.BeastBall -> "Beast Ball"
        BallTheme.LuxuryBall -> "Luxury Ball"
    }
}

fun ballThemeIcon(
    theme: BallTheme,
    iconPokeballBitmap: Bitmap?,
    iconGreatballBitmap: Bitmap?,
    iconUltraballBitmap: Bitmap?,
    iconMasterballBitmap: Bitmap?,
    iconLevelballBitmap: Bitmap?,
    iconLoveballBitmap: Bitmap?,
    iconNetballBitmap: Bitmap?,
    iconPremierballBitmap: Bitmap?,
    iconQuickballBitmap: Bitmap?,
    iconSafariballBitmap: Bitmap?,
    iconBeastballBitmap: Bitmap?,
    iconLuxuryballBitmap: Bitmap?
): Bitmap? {
    return when (theme) {
        BallTheme.None -> null
        BallTheme.PokeBall -> iconPokeballBitmap
        BallTheme.GreatBall -> iconGreatballBitmap
        BallTheme.UltraBall -> iconUltraballBitmap
        BallTheme.MasterBall -> iconMasterballBitmap
        BallTheme.LevelBall -> iconLevelballBitmap
        BallTheme.LoveBall -> iconLoveballBitmap
        BallTheme.NetBall -> iconNetballBitmap
        BallTheme.PremierBall -> iconPremierballBitmap
        BallTheme.QuickBall -> iconQuickballBitmap
        BallTheme.SafariBall -> iconSafariballBitmap
        BallTheme.BeastBall -> iconBeastballBitmap
        BallTheme.LuxuryBall -> iconLuxuryballBitmap
    }
}
