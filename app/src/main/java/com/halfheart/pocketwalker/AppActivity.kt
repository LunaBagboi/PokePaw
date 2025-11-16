package com.halfheart.pocketwalker

import AudioEngine
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

import com.halfheart.pocketwalkerlib.BUTTON_CENTER
import com.halfheart.pocketwalkerlib.BUTTON_LEFT
import com.halfheart.pocketwalkerlib.BUTTON_RIGHT
import com.halfheart.pocketwalkerlib.PocketWalkerNative
import com.yourpackage.TcpSocket
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.function.Function
import kotlin.concurrent.thread
import kotlin.experimental.xor

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

class AppActivity : ComponentActivity()  {
    private var canvasBitmap by mutableStateOf<Bitmap?>(null)

    private lateinit var pokeWalker: PocketWalkerNative

    private var romBytes: ByteArray? = null
    private var eepromBytes: ByteArray? = null

    private lateinit var romPickerLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var eepromPickerLauncher: ActivityResultLauncher<Array<String>>

    private lateinit var preferences: SharedPreferences

    private var palette = intArrayOf(
        0xCCCCCC,
        0x999999,
        0x666666,
        0x333333
    )

    private val EEPROM_SAVE_FILENAME = "eeprom_save.bin"

    private var didInitialize: Boolean = false

    private fun setTint(tint: Tint) {
        palette = when (tint) {
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
    }

    fun initializePokeWalkerIfReady() {
        val rom = romBytes
        val eeprom = eepromBytes

        if (rom == null || eeprom == null) {
            return
        }

        pokeWalker = PocketWalkerNative()
        pokeWalker.create(rom, eeprom)

        pokeWalker.onDraw { bytes ->
            canvasBitmap = createBitmap(bytes)
        }

        val audioEngine = AudioEngine()
        pokeWalker.onAudio { freq: Float, isFullVolume: Boolean ->
            // Read latest sound preferences so changes in the sidebar apply immediately
            val softChirp = preferences.getBoolean("soft_chirp_enabled", false)
            val volumeLevel = preferences.getInt("volume_level", 7).coerceIn(1, 10)

            // Map 1..10 to a smooth 0.2..1.0 range
            val volumeFactor = 0.2f + (volumeLevel - 1) * (0.8f / 9f)

            // Slightly louder base when the device requests full volume
            val base = if (isFullVolume) 0.8f else 0.5f

            audioEngine.render(freq, base * volumeFactor, 1.0f, soft = softChirp)
        }

        thread(priority = Thread.MAX_PRIORITY) {
            pokeWalker.start()
        }

        didInitialize = true
    }

    fun initializeTcp() {
        val socket = TcpSocket()

        socket.setOnConnect {
            println("[TCP] Connected")
        }

        socket.setOnClose {
            println("[TCP] Disconnected")
        }

        socket.setOnData { data ->
            data.forEach { byte ->
                println("RX: %02X".format(byte xor 0xAA.toByte()))
                pokeWalker.receiveSci3(byte)
            }
        }

        pokeWalker.onTransmitSci3 { byte ->
            println("TX: %02X".format(byte xor 0xAA.toByte()))
            socket.send(byteArrayOf(byte))
        }

        socket.connect("10.0.0.123", 8081)

        thread {
            Thread.sleep(3000)

            while (true) {
                if (!socket.isConnected() && !socket.isConnecting()) {
                    println("Attempting reconnection...")
                    socket.reconnect()
                    Thread.sleep(5000)
                } else {
                    Thread.sleep(1000)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences("pokewalker_prefs", Context.MODE_PRIVATE)

        // Enable fullscreen: draw behind system bars and hide status/navigation bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val savedRomUri = preferences.getString("rom_uri", null)?.let { Uri.parse(it) }
        val savedTint = preferences.getString("tint", Tint.None.name)
            ?.let { raw ->
                runCatching { Tint.valueOf(raw) }.getOrElse {
                    // Map old tint names to new ones for backwards compatibility
                    when (raw) {
                        "CinnabarIsland" -> Tint.Red
                        "Elysium" -> Tint.Blue
                        "DMG" -> Tint.Green
                        else -> Tint.None
                    }
                }
            } ?: Tint.None

        if (savedRomUri != null) {
            contentResolver.takePersistableUriPermission(
                savedRomUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            contentResolver.openInputStream(savedRomUri)?.use { input ->
                romBytes = input.readBytes()
            }
        }

        // Load internal persistent EEPROM save if it exists
        if (applicationContext.fileList().contains(EEPROM_SAVE_FILENAME)) {
            applicationContext.openFileInput(EEPROM_SAVE_FILENAME).use { input ->
                eepromBytes = input.readBytes()
            }
        }

        romPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult

            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            contentResolver.openInputStream(uri)?.use { input ->
                romBytes = input.readBytes()
            }

            preferences.edit().putString("rom_uri", uri.toString()).apply()

            initializePokeWalkerIfReady()
        }

        eepromPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@registerForActivityResult

            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                eepromBytes = bytes

                // Persist imported EEPROM into internal save file so progress can continue
                applicationContext.openFileOutput(EEPROM_SAVE_FILENAME, MODE_PRIVATE).use { output ->
                    output.write(bytes)
                }
            }

            preferences.edit().putString("eeprom_uri", uri.toString()).apply()

            initializePokeWalkerIfReady()
        }

        // Apply saved tint to the palette so the LCD uses the persisted tint immediately
        setTint(savedTint)

        setContent {
            PWApp(
                pokeWalker = if (::pokeWalker.isInitialized && didInitialize) pokeWalker else null,
                canvasBitmap = canvasBitmap,
                onLoadRom = { romPickerLauncher.launch(arrayOf("*/*")) },
                onLoadEeprom = { eepromPickerLauncher.launch(arrayOf("*/*")) },
                onTintSelected = { tint ->
                    setTint(tint)
                    preferences.edit().putString("tint", tint.name).apply()
                },
                initialSelectedTint = savedTint
            )
        }

        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(sensorListener, it, SENSOR_INTERVAL_US)
        }

        initializePokeWalkerIfReady()
        if (::pokeWalker.isInitialized) {
            initializeTcp()
        }
    }

    override fun onStop() {
        super.onStop()
        // Persist current EEPROM state to internal save if emulator is running
        if (::pokeWalker.isInitialized) {
            val currentEeprom = pokeWalker.getEepromBuffer()
            applicationContext.openFileOutput(EEPROM_SAVE_FILENAME, MODE_PRIVATE).use { output ->
                output.write(currentEeprom)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::pokeWalker.isInitialized) {
            pokeWalker.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::pokeWalker.isInitialized) {
            pokeWalker.resume()
        }
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val SENSOR_TARGET_HZ = 50
    private val SENSOR_INTERVAL_NS = 1_000_000_000L / SENSOR_TARGET_HZ
    private val SENSOR_INTERVAL_US = 1_000_000 / SENSOR_TARGET_HZ
    private var lastSensorTimestamp = 0L

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!didInitialize) return
            if (event.timestamp - lastSensorTimestamp < SENSOR_INTERVAL_NS) return

            lastSensorTimestamp = event.timestamp

            val x = event.values[0] / SensorManager.GRAVITY_EARTH
            val y = event.values[1] / SensorManager.GRAVITY_EARTH
            val z = event.values[2] / SensorManager.GRAVITY_EARTH

            pokeWalker.setAccelerationData(x, y, z)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }


    private fun createBitmap(paletteIndices: ByteArray): Bitmap {
        val width = 96
        val height = 64

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)

        for (i in pixels.indices) {
            var paletteIndex = paletteIndices[i].toInt() and 0xFF
            if (paletteIndex >= palette.size) {
                paletteIndex = 0
            }

            val color = palette[paletteIndex]
            // Use raw palette colors; contrast adjustment disabled
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    //DISABLEDCODE: legacy contrast adjustment logic kept for reference
//    private fun createBitmapWithContrast(paletteIndices: ByteArray): Bitmap {
//        val width = 96
//        val height = 64
//
//        val contrast = pokeWalker.getContrast().toInt()
//        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        val pixels = IntArray(width * height)
//
//        for (i in pixels.indices) {
//            var paletteIndex = paletteIndices[i].toInt() and 0xFF
//            if (paletteIndex >= palette.size) {
//                paletteIndex = 0
//            }
//
//            val color = palette[paletteIndex]
//            val r = (color shr 16) and 0xFF
//            val g = (color shr 8) and 0xFF
//            val b = color and 0xFF
//
//            val (adjustedR, adjustedG, adjustedB) = if (paletteIndex == 0) {
//                Triple(r, g, b)
//            } else {
//                Triple(
//                    applyContrast(r, contrast),
//                    applyContrast(g, contrast),
//                    applyContrast(b, contrast)
//                )
//            }
//
//            pixels[i] = (0xFF shl 24) or (adjustedR shl 16) or (adjustedG shl 8) or adjustedB
//        }
//
//        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
//        return bitmap
//    }
//
//    private fun applyContrast(colorValue: Int, contrast: Int): Int {
//
//        val clampedContrast = contrast.coerceIn(0, 9)
//
//        if (colorValue == 0xCC) {
//            return colorValue
//        }
//
//        val contrastMultiplier = when (clampedContrast) {
//            0 -> 0.2f
//            1 -> 0.4f
//            2 -> 0.6f
//            3 -> 0.8f
//            4 -> 1.0f
//            5 -> 1.2f
//            6 -> 1.4f
//            7 -> 1.6f
//            8 -> 1.8f
//            9 -> 2.0f
//            else -> 1.0f
//        }
//
//        val referenceValues = mapOf(
//            0x99 to 153,
//            0x66 to 102,
//            0x33 to 51
//        )
//
//        val referenceValue = referenceValues[colorValue]
//        if (referenceValue != null) {
//            val distanceFromMid = referenceValue - 128
//
//            val adjustedDistance = (distanceFromMid * contrastMultiplier).toInt()
//            val newValue = 128 + adjustedDistance
//
//            return newValue.coerceIn(0, 255)
//        }
//
//        val distanceFromMid = colorValue - 128
//        val adjustedDistance = (distanceFromMid * contrastMultiplier).toInt()
//        val newValue = 128 + adjustedDistance
//
//        return newValue.coerceIn(0, 255)
//    }
}

fun Bitmap.sampleEdgeColor(top: Boolean): Color {
    if (width <= 0 || height <= 0) return Color(0xFF000000)

    val y = if (top) 0 else (height - 1).coerceAtLeast(0)
    val x = (width / 2).coerceIn(0, width - 1)

    val pixel = getPixel(x, y)
    val a = (pixel ushr 24) and 0xFF
    val r = (pixel ushr 16) and 0xFF
    val g = (pixel ushr 8) and 0xFF
    val b = pixel and 0xFF

    return Color(r, g, b, a)
}

@Composable
fun PWApp(
    pokeWalker: PocketWalkerNative?,
    canvasBitmap: Bitmap?,
    onLoadRom: () -> Unit,
    onLoadEeprom: () -> Unit,
    onTintSelected: (Tint) -> Unit,
    initialSelectedTint: Tint = Tint.None
) {
    var filesExpanded by remember { mutableStateOf(true) }
    var appearanceExpanded by remember { mutableStateOf(true) }
    var soundExpanded by remember { mutableStateOf(true) }
    var cheatsExpanded by remember { mutableStateOf(false) }
    var debugExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("pokewalker_prefs", Context.MODE_PRIVATE) }

    val pokeballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/pokeball.png"))
        }.getOrNull()
    }

    val greatballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/greatball.png"))
        }.getOrNull()
    }

    val ultraballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/ultraball.png"))
        }.getOrNull()
    }

    val masterballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/masterball.png"))
        }.getOrNull()
    }

    val levelballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/levelball.png"))
        }.getOrNull()
    }

    val premierballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/premierball.png"))
        }.getOrNull()
    }

    val quickballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/quickball.png"))
        }.getOrNull()
    }

    val safariballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/safariball.png"))
        }.getOrNull()
    }

    val iconPokeballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-pokeball.png"))
        }.getOrNull()
    }

    val iconGreatballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-greatball.png"))
        }.getOrNull()
    }

    val iconUltraballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-ultraball.png"))
        }.getOrNull()
    }

    val iconMasterballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-masterball.png"))
        }.getOrNull()
    }

    val iconLevelballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-levelball.png"))
        }.getOrNull()
    }

    val iconLoveballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-loveball.png"))
        }.getOrNull()
    }

    val iconNetballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-netball.png"))
        }.getOrNull()
    }

    val iconPremierballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-premierball.png"))
        }.getOrNull()
    }

    val iconQuickballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-quickball.png"))
        }.getOrNull()
    }

    val iconSafariballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-safariball.png"))
        }.getOrNull()
    }

    val iconBeastballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-beastball.png"))
        }.getOrNull()
    }

    val iconLuxuryballBitmap = remember {
        runCatching {
            BitmapFactory.decodeStream(context.assets.open("background-assets/icon-luxuryball.png"))
        }.getOrNull()
    }

    var selectedTint by remember { mutableStateOf(initialSelectedTint) }
    var tintMenuExpanded by remember { mutableStateOf(false) }
    var sidebarOpen by remember { mutableStateOf(false) }
    var colorizationEnabled by remember {
        mutableStateOf(preferences.getBoolean("colorization_enabled", false))
    }
    var ballThemeMenuExpanded by remember { mutableStateOf(false) }
    var selectedBallTheme by remember {
        mutableStateOf(
            preferences.getString("ball_theme", BallTheme.PokeBall.name)
                ?.let { raw ->
                    runCatching { BallTheme.valueOf(raw) }.getOrElse { BallTheme.PokeBall }
                } ?: BallTheme.PokeBall
        )
    }
    var shaderMenuExpanded by remember { mutableStateOf(false) }
    var selectedShader by remember {
        mutableStateOf(
            preferences.getString("shader_option", ShaderOption.None.name)
                ?.let { raw ->
                    runCatching { ShaderOption.valueOf(raw) }.getOrElse { ShaderOption.None }
                } ?: ShaderOption.None
        )
    }
    var shadeLevel by remember {
        mutableStateOf(preferences.getInt("shade_level", 5).coerceIn(1, 10))
    }
    var lcdSizeMenuExpanded by remember { mutableStateOf(false) }
    var selectedLcdSize by remember { mutableStateOf("Small") }
    var softChirpEnabled by remember {
        mutableStateOf(preferences.getBoolean("soft_chirp_enabled", false))
    }
    var volumeLevel by remember {
        mutableStateOf(preferences.getInt("volume_level", 7).coerceIn(1, 10))
    }
    var hapticsStrength by remember {
        mutableStateOf(preferences.getInt("haptics_strength", 2).coerceIn(1, 3))
    }
    var sidebarRowSpacingLevel by remember {
        mutableStateOf(preferences.getInt("sidebar_row_spacing_level", 4).coerceIn(0, 10))
    }

    val isLoaded = pokeWalker != null

    val controlWidth = 140.dp

    // Lightest color of each tint palette, used for the bezel directly behind the LCD
    val lcdBackground = when (selectedTint) {
        Tint.None -> Color(0xFFCCCCCC)
        Tint.SGB -> Color(0xFFFFEFFF)
        Tint.Green -> Color(0xFFE0F8D0)
        Tint.Red -> Color(0xFFFFF4F4)
        Tint.Blue -> Color(0xFFF5F5F7)
    }

    val sidebarWidth = 260.dp
    val sidebarOffsetX by animateDpAsState(
        targetValue = if (sidebarOpen) 0.dp else -sidebarWidth,
        label = "sidebarOffsetX"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF050509))
            .pointerInput(sidebarOpen) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // Only start an open gesture from the very left edge when sidebar is closed
                        if (!sidebarOpen && offset.x > 48.dp.toPx()) {
                            return@detectDragGestures
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (!sidebarOpen && change.position.x < 200f && dragAmount.x > 40f) {
                            sidebarOpen = true
                            change.consume()
                        } else if (sidebarOpen && dragAmount.x < -40f) {
                            // Allow swiping left anywhere to close as well
                            sidebarOpen = false
                            change.consume()
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (selectedBallTheme != BallTheme.None) {
                val defaultTop = Color(0xFFD32329)
                val defaultBottom = Color(0xffdadade)

                val topColor = when (selectedBallTheme) {
                    BallTheme.UltraBall -> Color(0xFF181414)
                    BallTheme.SafariBall -> Color(0xFF76BD25)
                    BallTheme.QuickBall -> Color(0xFF4DA2CA)
                    BallTheme.PremierBall -> Color(0xFFDBDBDF)
                    BallTheme.MasterBall -> Color(0xFF6700C0)
                    BallTheme.LevelBall -> Color(0xFF161616)
                    BallTheme.GreatBall -> Color(0xFF007ED7)
                    else -> defaultTop
                }

                val bottomColor = defaultBottom

                val bgBitmap: Bitmap? = when (selectedBallTheme) {
                    BallTheme.PokeBall -> pokeballBitmap
                    BallTheme.GreatBall -> greatballBitmap
                    BallTheme.UltraBall -> ultraballBitmap
                    BallTheme.MasterBall -> masterballBitmap
                    BallTheme.LevelBall -> levelballBitmap
                    BallTheme.PremierBall -> premierballBitmap
                    BallTheme.QuickBall -> quickballBitmap
                    BallTheme.SafariBall -> safariballBitmap
                    else -> null
                }

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

            Box(
                Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 16.dp)
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

            PWButton(
                pokeWalker, BUTTON_CENTER, size = 46, top = 260,
                modifier = Modifier.align(Alignment.Center)
            )
            PWButton(
                pokeWalker, BUTTON_LEFT, size = 36, top = 240, end = 128,
                modifier = Modifier.align(Alignment.Center)
            )
            PWButton(
                pokeWalker, BUTTON_RIGHT, size = 36, top = 240, start = 128,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Sidebar uses the darkest tone of the currently selected tint palette so it visually
        // matches the active tint.
        val sidebarBackground = when (selectedTint) {
            Tint.None -> Color(0xFF111119)
            Tint.SGB -> Color(0xFF181101)
            Tint.Green -> Color(0xFF081820)
            Tint.Red -> Color(0xFF3C1212)
            Tint.Blue -> Color(0xFF1E1E29)
        }

        val dropdownBackground = sidebarBackground.copy(alpha = 0.92f)

        val sidebarShape = RoundedCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 0.dp)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(sidebarWidth)
                .offset(x = sidebarOffsetX)
                .pointerInput(sidebarOpen) {
                    detectDragGestures { change, dragAmount ->
                        if (sidebarOpen && dragAmount.x < -40f) {
                            sidebarOpen = false
                            change.consume()
                        }
                    }
                }
                .shadow(10.dp, sidebarShape, clip = true)
                .background(sidebarBackground.copy(alpha = 0.9f), sidebarShape)
                .border(1.dp, Color(0xFF2A2A3A), sidebarShape)
                .padding(16.dp)
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 24.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy((sidebarRowSpacingLevel + 2).dp)
            ) {
                Text(
                    text = "PokePaw",
                    color = Color(0xFFF5F5F7),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Text(
                    text = (if (filesExpanded) "▾ " else "▸ ") + "Files",
                    color = Color(0xFFAAAAFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { filesExpanded = !filesExpanded }
                        .padding(vertical = 10.dp)
                )

                if (filesExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),

                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ROM",
                                color = Color(0xFFCBCBE5),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )

                            if (isLoaded) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = null,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                )
                            } else {
                                Button(
                                    onClick = onLoadRom,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                ) {
                                    Text("Select")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, top = 6.dp, bottom = 10.dp),

                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "EEPROM",
                                color = Color(0xFFCBCBE5),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )

                            if (isLoaded) {
                                Checkbox(
                                    checked = true,
                                    onCheckedChange = null,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                )
                            } else {
                                Button(
                                    onClick = onLoadEeprom,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                ) {
                                    Text("Select")
                                }
                            }
                        }
                    }
                }

                Text(
                    text = (if (appearanceExpanded) "▾ " else "▸ ") + "Appearance",
                    color = Color(0xFFAAAAFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { appearanceExpanded = !appearanceExpanded }
                        .padding(vertical = 10.dp)
                )

                if (appearanceExpanded) {
                    val tintLabel = when (selectedTint) {
                        Tint.None -> "None"
                        Tint.SGB -> "SGB"
                        Tint.Green -> "Green"
                        Tint.Red -> "Red"
                        Tint.Blue -> "Blue"
                    }

                    fun tintColors(tint: Tint): List<Color> {
                        return when (tint) {
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

                    fun ballThemeIcon(theme: BallTheme): Bitmap? {
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Colorization",
                            color = Color(0xFFB0B0C8),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = colorizationEnabled,
                            onCheckedChange = { checked ->
                                colorizationEnabled = checked
                                preferences.edit().putBoolean("colorization_enabled", checked).apply()
                            }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
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
                                val iconBitmap = ballThemeIcon(selectedBallTheme)

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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(dropdownBackground)
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
                                                    val iconBitmap = ballThemeIcon(theme)
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
                                                selectedBallTheme = theme
                                                preferences.edit().putString("ball_theme", theme.name).apply()
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
                                    tintColors(selectedTint).forEach { color ->
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
                                                    tintColors(tint).forEach { color ->
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
                                            selectedTint = tint
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
                                .pointerInput(Unit) {
                                    detectDragGestures { change, _ ->
                                        val widthPx = size.width.toFloat().coerceAtLeast(1f)
                                        val x = change.position.x.coerceIn(0f, widthPx)
                                        val fraction = x / widthPx
                                        val newLevel = (fraction * 10).toInt().coerceIn(0, 9) + 1
                                        shadeLevel = newLevel
                                        preferences.edit().putInt("shade_level", newLevel).apply()
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
                                                if (level <= shadeLevel) Color(0xFFEFEFFF) else Color(0xFF404060),
                                                RoundedCornerShape(3.dp)
                                            )
                                            .clickable {
                                                shadeLevel = level
                                                preferences.edit().putInt("shade_level", level).apply()
                                            }
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
                                        ShaderOption.ScaleFX -> "ScaleFX"
                                        ShaderOption.Grid -> "Grid"
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
                                            selectedShader = option
                                            preferences.edit().putString("shader_option", option.name).apply()
                                            shaderMenuExpanded = false
                                        }
                                    )
                                }

                                shaderItem("None", ShaderOption.None)
                                shaderItem("ScaleFX", ShaderOption.ScaleFX)
                                shaderItem("Grid", ShaderOption.Grid)
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
                                        selectedLcdSize = "Small"
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
                                        selectedLcdSize = "Large"
                                        lcdSizeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = (if (soundExpanded) "▾ " else "▸ ") + "Sound",
                    color = Color(0xFFAAAAFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { soundExpanded = !soundExpanded }
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

                        Switch(
                            checked = softChirpEnabled,
                            onCheckedChange = { checked ->
                                softChirpEnabled = checked
                                preferences.edit().putBoolean("soft_chirp_enabled", checked).apply()
                            }
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
                                        volumeLevel = newLevel
                                        preferences.edit().putInt("volume_level", newLevel).apply()
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
                                            .clickable {
                                                volumeLevel = level
                                                preferences.edit().putInt("volume_level", level).apply()
                                            }
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
                                            .clickable {
                                                hapticsStrength = level
                                                preferences.edit().putInt("haptics_strength", level).apply()
                                            }
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = (if (cheatsExpanded) "▾ " else "▸ ") + "Cheats",
                    color = Color(0xFFAAAAFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { cheatsExpanded = !cheatsExpanded }
                        .padding(vertical = 10.dp)
                )

                if (cheatsExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "No cheats configured yet",
                            color = Color(0xFFB0B0C8),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                        )
                    }
                }

                Text(
                    text = (if (debugExpanded) "▾ " else "▸ ") + "Debug",
                    color = Color(0xFFAAAAFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { debugExpanded = !debugExpanded }
                        .padding(vertical = 10.dp)
                )

                if (debugExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Row Spacing",
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
                                        val newLevel = (fraction * 10).toInt().coerceIn(0, 10)
                                        sidebarRowSpacingLevel = newLevel
                                        preferences.edit().putInt("sidebar_row_spacing_level", newLevel).apply()
                                        change.consume()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                (0..10).forEach { level ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(6.dp)
                                            .padding(horizontal = 1.dp)
                                            .background(
                                                if (level <= sidebarRowSpacingLevel) Color(0xFFEFEFFF) else Color(0xFF404060),
                                                RoundedCornerShape(3.dp)
                                            )
                                            .clickable {
                                                sidebarRowSpacingLevel = level
                                                preferences.edit().putInt("sidebar_row_spacing_level", level).apply()
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
    var isPressed by remember { mutableStateOf(false) }

    val buttonOutlineColor = Color(0xFFCFCFCF)
    val buttonColor = Color(0xFFF3F3F3)
    val pressedButtonColor = Color(0xFFDFDFDF)

    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .padding(top = top.dp, bottom = bottom.dp, start = start.dp, end = end.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isPressed = true
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        pokeWalker?.press(button)
                        tryAwaitRelease()
                        isPressed = false
                        delay(100) // allow time for read to catch up, while still having button down time
                        pokeWalker?.release(button)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .background(if (isPressed) pressedButtonColor else buttonColor, CircleShape)
                .border(BorderStroke(2.dp, buttonOutlineColor), CircleShape)
                .align(Alignment.Center)

        )
    }
}

enum class Tint {
    None,
    SGB,
    Green,
    Red,
    Blue
}

enum class ShaderOption {
    None,
    ScaleFX,
    Grid
}