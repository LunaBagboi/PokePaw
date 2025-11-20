package com.bagboi.pokepaw

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
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
import com.bagboi.pokepaw.ShaderOption
import com.bagboi.pokepaw.applyShaderOption
import com.bagboi.pokepaw.getCurrentShaderOption
import com.bagboi.pokepaw.PWLcd
import com.bagboi.pokepaw.BallTheme
import com.bagboi.pokepaw.BallThemeBackground
import com.bagboi.pokepaw.ballThemeIcon
import com.bagboi.pokepaw.ballThemeLabel
import com.bagboi.pokepaw.Tint
import com.bagboi.pokepaw.tintPalette
import com.bagboi.pokepaw.tintLcdBackground
import com.bagboi.pokepaw.tintSidebarBackground
import com.bagboi.pokepaw.tintLabelFor
import com.bagboi.pokepaw.tintColorsFor

import com.halfheart.pocketwalkerlib.BUTTON_CENTER
import com.halfheart.pocketwalkerlib.BUTTON_LEFT
import com.halfheart.pocketwalkerlib.BUTTON_RIGHT
import com.halfheart.pocketwalkerlib.PocketWalkerNative
import com.yourpackage.TcpSocket
import com.bagboi.pokepaw.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import java.util.function.Function
import kotlin.concurrent.thread
import kotlin.experimental.xor
import org.json.JSONObject

import com.bagboi.pokepaw.BaselineStepDetector
import com.bagboi.pokepaw.StepFusionFilter
import android.util.Log

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

    private val EEPROM_SAVE_FILENAME = "eeprom_pokepal.bin"

    private var didInitialize: Boolean = false

    private data class WalkerSpriteMeta(
        val id: String,
        val dex: Int,
        val gender: String,
        val file: String,
        val form: String?,
        val name: String
    )

    private val walkerSpriteMeta by lazy {
        val list = mutableListOf<WalkerSpriteMeta>()
        try {
            assets.open("mapped-sprite-data.json").use { input ->
                val text = input.bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = root.getJSONObject(key)
                    val dexStr = obj.optString("dex", "0")
                    val dex = dexStr.toIntOrNull() ?: continue
                    val gender = obj.optString("gender", "all")
                    val file = obj.optString("file", "")
                    val form = if (obj.has("form")) obj.optString("form", null) else null
                    val name = obj.optString("name", "")
                    if (file.isNotEmpty()) {
                        list += WalkerSpriteMeta(key, dex, gender, file, form, name)
                    }
                }
            }
        } catch (_: Exception) {
        }
        list
    }

    private fun findWalkerSpriteMeta(
        species: Int,
        isFemale: Boolean,
        hasForm: Boolean,
        variant: Int
    ): WalkerSpriteMeta? {
        if (species <= 0) return null
        val dexNumber = species

        val candidates = walkerSpriteMeta.filter { it.dex == dexNumber }
        if (candidates.isEmpty()) return null

        if (dexNumber == 201 && hasForm && candidates.size > 1) {
            val orderedForms = listOf(
                "A", "B", "C", "D", "E", "F", "G", "H", "I", "J",
                "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T",
                "U", "V", "W", "X", "Y", "Z",
                "Exclamation Mark", "Question Mark"
            )

            val clampedVariant = variant.coerceIn(0, orderedForms.lastIndex)
            val targetForm = orderedForms[clampedVariant]
            val formMatch = candidates.firstOrNull { it.form.equals(targetForm, ignoreCase = true) }
            if (formMatch != null) return formMatch
        }

        if (hasForm) {
            val formCandidates = candidates.filter { it.form != null }
            if (formCandidates.size > 1) {
                val sortedForms = formCandidates.sortedBy { it.id }
                val index = if (variant >= 0) variant % sortedForms.size else 0
                return sortedForms[index]
            }
        }

        val genderKey = if (isFemale) "female" else "male"

        val genderMatch = candidates.firstOrNull { it.gender.equals(genderKey, ignoreCase = true) }
        if (genderMatch != null) return genderMatch

        val allMatch = candidates.firstOrNull { it.gender.equals("all", ignoreCase = true) }
        if (allMatch != null) return allMatch

        return candidates.firstOrNull()
    }

    private fun setTint(tint: Tint) {
        palette = tintPalette(tint)
    }

    private fun formatWalkerName(meta: WalkerSpriteMeta): String {
        return meta.name.ifEmpty { "Dex ${meta.dex}" }
    }

    fun initializePokeWalkerIfReady() {
        val rom = romBytes
        val eeprom = eepromBytes

        if (rom == null || eeprom == null) {
            return
        }

        pokeWalker = PocketWalkerNative()
        pokeWalker.create(rom, eeprom)

        val initialColorMode = preferences.getBoolean("colorization_enabled", false)
        pokeWalker.setColorMode(initialColorMode)

        pokeWalker.onDraw { bytes ->
            val prefColor = preferences.getBoolean("colorization_enabled", false)
            val useColor = prefColor && hasWalkerColorSprite
            if (useColor) {
                val colorFrame = pokeWalker.getColorFrame()
                val colorBitmap = createHybridColorBitmap(bytes, colorFrame)
                canvasBitmap = applyCurrentShaderOption(colorBitmap)
            } else {
                val baseBitmap = createBitmap(bytes)
                canvasBitmap = applyCurrentShaderOption(baseBitmap)
            }
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
        startWalkerSpriteWatcher()
        startRouteWatcher()
    }

    fun initializeTcp(host: String, port: Int) {
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

        socket.connect(host, port)

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
                runCatching { Tint.valueOf(raw) }.getOrElse { Tint.None }
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
            val showDebugOverlayState = remember {
                mutableStateOf(preferences.getBoolean("debug_overlay_enabled", false))
            }

            Box(Modifier.fillMaxSize()) {
                PWApp(
                    pokeWalker = if (::pokeWalker.isInitialized && didInitialize) pokeWalker else null,
                    canvasBitmap = canvasBitmap,
                    onLoadRom = { romPickerLauncher.launch(arrayOf("*/*")) },
                    onLoadEeprom = { eepromPickerLauncher.launch(arrayOf("*/*")) },
                    onTintSelected = { tint ->
                        setTint(tint)
                        preferences.edit().putString("tint", tint.name).apply()
                    },
                    initialSelectedTint = savedTint,
                    showDebugOverlay = showDebugOverlayState.value,
                    onShowDebugOverlayChange = { enabled ->
                        showDebugOverlayState.value = enabled
                        preferences.edit().putBoolean("debug_overlay_enabled", enabled).apply()
                    }
                )

                if (showDebugOverlayState.value) {
                    val routeId = currentRouteId
                    val special = isSpecialRoute
                    val walkerDex = lastWalkerDex
                    val walkerVariant = lastWalkerVariant
                    val walkerShiny = lastWalkerIsShiny
                    val walkerFemale = lastWalkerIsFemale
                    val walkerHasForm = lastWalkerHasForm
                    val walkerName = lastWalkerName
                    val walkerFormName = lastWalkerFormName

                    val routePart = when {
                        routeId == null -> "No route selected"
                        special -> "Route $routeId (special)"
                        else -> "Route $routeId"
                    }

                    val wattsPart = "${currentWatts.coerceAtLeast(0)} W"

                    val walkerPart = if (walkerDex != null) {
                        buildString {
                            if (walkerName != null) {
                                append("$walkerName ($walkerDex)")
                            } else {
                                append("Dex $walkerDex")
                            }
                            if (walkerVariant != null) {
                                append(" – variant $walkerVariant")
                                if (walkerHasForm && walkerFormName != null && walkerFormName.isNotEmpty()) {
                                    append(" ($walkerFormName)")
                                }
                            }

                            append(", ")
                            append(if (walkerFemale) "female" else "male")
                            if (walkerShiny) append(" – shiny ✨")
                        }
                    } else {
                        "No walker on device"
                    }

                    val baselineSteps = baselineStepCount
                    val fusedSteps = fusedStepCount

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color(0x80000000))
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Text(
                            text = "$routePart • $wattsPart • $walkerPart",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "Baseline: $baselineSteps • Fused: $fusedSteps",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        sensorManager = applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let {
            sensorManager.registerListener(sensorListener, it, SENSOR_INTERVAL_US)
        }

        // Optional fusion sensors (no extra permissions required)
        linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        linearAccelSensor?.let {
            sensorManager.registerListener(fusionSensorListener, it, SENSOR_INTERVAL_US)
        }
        gyroSensor?.let {
            sensorManager.registerListener(fusionSensorListener, it, SENSOR_INTERVAL_US)
        }

        initializePokeWalkerIfReady()
        if (::pokeWalker.isInitialized) {
            val irEnabled = preferences.getBoolean("ir_enabled", false)
            if (irEnabled) {
                val irHost = preferences.getString("ir_host", "10.0.0.123") ?: "10.0.0.123"
                val irPort = preferences.getInt("ir_port", 8081)
                initializeTcp(irHost, irPort)
            }
        }

        // Initialize baseline step detector
        baselineStepDetector = BaselineStepDetector().apply {
            listener = object : BaselineStepDetector.Listener {
                override fun onStep(timestampNanos: Long) {
                    baselineStepCount += 1

                    // Forward baseline steps into the fusion filter
                    stepFusionFilter?.onBaselineStep(timestampNanos)
                }
            }
        }

        // Initialize step fusion filter
        stepFusionFilter = StepFusionFilter().apply {
            listener = object : StepFusionFilter.Listener {
                override fun onFusedStep(timestampNanos: Long) {
                    fusedStepCount += 1

                    // Debug: inspect accelerometer registers around the region the
                    // ROM reads for samples (0x04/0x06/0x08) when we accept a step.
                    if (::pokeWalker.isInitialized) {
                        try {
                            val window = pokeWalker.getAccelWindow(0, 0x10)
                            if (window.size >= 0x09) {
                                val b4 = window[0x04].toInt()
                                val b6 = window[0x06].toInt()
                                val b8 = window[0x08].toInt()
                                Log.d(
                                    "AccelWindow",
                                    "step#$fusedStepCount regs[4,6,8]=[$b4,$b6,$b8]"
                                )
                            } else {
                                Log.d("AccelWindow", "window too small: size=${window.size}")
                            }
                        } catch (t: Throwable) {
                            Log.d("AccelWindow", "error reading accel window: ${t.message}")
                        }
                    }

                    // Forward fused steps into the native emulator so the ROM's
                    // handleAccelSteps pipeline can consume them.
                    if (::pokeWalker.isInitialized) {
                        try {
                            pokeWalker.addFusedSteps(1)
                        } catch (_: Throwable) {
                        }
                    }
                }
            }
        }
    }

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var linearAccelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null

    private val SENSOR_TARGET_HZ = 50
    private val SENSOR_INTERVAL_NS = 1_000_000_000L / SENSOR_TARGET_HZ
    private val SENSOR_INTERVAL_US = 1_000_000 / SENSOR_TARGET_HZ
    private var lastSensorTimestamp = 0L

    private var lastFusionLinearTimestamp = 0L
    private var lastFusionGyroTimestamp = 0L

    private var lastWalkerDex: Int? = null
    private var lastWalkerName: String? = null

    private var lastWalkerVariant: Int? = null
    private var lastWalkerIsShiny: Boolean = false
    private var lastWalkerIsFemale: Boolean = false
    private var lastWalkerHasForm: Boolean = false
    private var lastWalkerFormName: String? = null

    private var hasWalkerColorSprite: Boolean = false

    private var currentRouteId: Int? = null
    private var isSpecialRoute: Boolean = false
    private var currentWatts: Int = 0

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!didInitialize) return
            if (event.timestamp - lastSensorTimestamp < SENSOR_INTERVAL_NS) return

            lastSensorTimestamp = event.timestamp

            val normX = event.values[0] / SensorManager.GRAVITY_EARTH
            val normY = event.values[1] / SensorManager.GRAVITY_EARTH
            val normZ = event.values[2] / SensorManager.GRAVITY_EARTH

            // Apply an emulator-only gain so the ROM sees stronger motion,
            // while Baseline/Fusion continue to use the normalized values.
            val gain = 2.0f
            val emuX = normX * gain
            val emuY = normY * gain
            val emuZ = normZ * gain

            // Feed the native emulator and baseline step detector
            pokeWalker.setAccelerationData(emuX, emuY, emuZ)
            baselineStepDetector?.addSample(normX, normY, normZ, event.timestamp)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private var baselineStepDetector: BaselineStepDetector? = null
    private var baselineStepCount: Int = 0

    private var stepFusionFilter: StepFusionFilter? = null
    private var fusedStepCount: Int = 0

    private val fusionSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    // Throttle fusion processing to target rate
                    if (event.timestamp - lastFusionLinearTimestamp < SENSOR_INTERVAL_NS) return
                    lastFusionLinearTimestamp = event.timestamp
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]
                    stepFusionFilter?.onLinearAccelSample(ax, ay, az, event.timestamp)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    if (event.timestamp - lastFusionGyroTimestamp < SENSOR_INTERVAL_NS) return
                    lastFusionGyroTimestamp = event.timestamp
                    val gx = event.values[0]
                    val gy = event.values[1]
                    val gz = event.values[2]
                    stepFusionFilter?.onGyroSample(gx, gy, gz, event.timestamp)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun startRouteWatcher() {
        if (!::pokeWalker.isInitialized) return

        lifecycleScope.launch {
            while (didInitialize) {
                try {
                    currentRouteId = pokeWalker.getCurrentRouteId()
                    isSpecialRoute = pokeWalker.isSpecialRoute()
                    currentWatts = pokeWalker.getCurrentWatts()
                } catch (_: Exception) {
                }

                delay(1000L)
            }
        }
    }

    private fun startWalkerSpriteWatcher() {
        if (!::pokeWalker.isInitialized) return

        lifecycleScope.launch {
            while (didInitialize) {
                try {
                    val useColor = preferences.getBoolean("colorization_enabled", false)
                    if (!useColor) {
                        delay(1000L)
                        continue
                    }

                    val info = pokeWalker.getWalkerVariantInfo()
                    if (info.size >= 5) {
                        val species = info[0]
                        val variant = info[1]
                        val isFemale = info[2] != 0
                        val isShiny = info[3] != 0
                        val hasForm = info[4] != 0

                        if (species != lastWalkerDex || variant != (lastWalkerVariant ?: -1) || isShiny != lastWalkerIsShiny || isFemale != lastWalkerIsFemale || hasForm != lastWalkerHasForm) {
                            val meta = findWalkerSpriteMeta(species, isFemale, hasForm, variant)
                            if (meta != null) {
                                val baseDir = if (isShiny) "pokemon-shiny-sprites" else "pokemon-sprites"
                                val filename = "$baseDir/${meta.id}.png"

                                try {
                                    assets.open(filename).use { inputStream ->
                                        val fullBitmap = BitmapFactory.decodeStream(inputStream) ?: return@use
                                        val width = fullBitmap.width
                                        val height = fullBitmap.height

                                        val pixels = IntArray(width * height)
                                        fullBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                                        pokeWalker.setColorSprite("walker", pixels, width, height)
                                        lastWalkerDex = species
                                        lastWalkerVariant = variant
                                        lastWalkerIsShiny = isShiny
                                        lastWalkerIsFemale = isFemale
                                        lastWalkerHasForm = hasForm
                                        lastWalkerName = formatWalkerName(meta)
                                        lastWalkerFormName = meta.form

                                        hasWalkerColorSprite = true
                                    }
                                } catch (_: Exception) {
                                    hasWalkerColorSprite = false
                                }
                            } else {
                                hasWalkerColorSprite = false
                            }
                        }
                    }
                } catch (_: Exception) {
                }

                delay(1000L)
            }
        }
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

    private fun createHybridColorBitmap(paletteIndices: ByteArray, colorFrame: IntArray): Bitmap {
        val width = 96
        val height = 64
        val pixelCount = width * height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(pixelCount)

        // Native base LCD palette (including alpha) used by Lcd::colorBuffer
        val basePalette = intArrayOf(
            0xFFB7B8B0.toInt(), // PALETTE[0]
            0xFF808173.toInt(),
            0xFF666559.toInt(),
            0xFF1F1A17.toInt()
        )

        for (i in 0 until pixelCount) {
            val hasColorFrame = i < colorFrame.size
            val nativePixel = if (hasColorFrame) colorFrame[i] else 0

            val isBaseLcdColor = hasColorFrame && basePalette.any { it == nativePixel }

            if (!hasColorFrame || isBaseLcdColor) {
                // Use original grayscale indices + tint palette for non-sprite content.
                val paletteIndex = (paletteIndices.getOrNull(i)?.toInt() ?: 0) and 0xFF
                val safeIndex = if (paletteIndex in palette.indices) paletteIndex else 0
                val color = palette[safeIndex]
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                outPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            } else {
                // Use full native ARGB for colored sprite pixels.
                outPixels[i] = nativePixel
            }
        }

        bitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun createColorBitmap(colorFrame: IntArray): Bitmap {
        val width = 96
        val height = 64

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (colorFrame.size >= width * height) {
            bitmap.setPixels(colorFrame, 0, width, 0, 0, width, height)
        }
        return bitmap
    }

    private fun applyCurrentShaderOption(bitmap: Bitmap): Bitmap {
        val option = getCurrentShaderOption(preferences)
        return applyShaderOption(bitmap, option)
    }

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
    initialSelectedTint: Tint = Tint.None,
    showDebugOverlay: Boolean,
    onShowDebugOverlayChange: (Boolean) -> Unit
) {
    var filesExpanded by remember { mutableStateOf(true) }
    var appearanceExpanded by remember { mutableStateOf(true) }
    var soundExpanded by remember { mutableStateOf(true) }
    var cheatsExpanded by remember { mutableStateOf(false) }
    var debugExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("pokewalker_prefs", Context.MODE_PRIVATE) }

    val ballBackgroundBitmaps = rememberBallBackgroundBitmaps()
    val ballIconBitmaps = rememberBallIconBitmaps()

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
    var disableSleepCheatEnabled by remember {
        mutableStateOf(preferences.getBoolean("cheat_disable_sleep", false))
    }
    var irEnabled by remember {
        mutableStateOf(preferences.getBoolean("ir_enabled", false))
    }
    var irHost by remember {
        mutableStateOf(preferences.getString("ir_host", "10.0.0.123") ?: "10.0.0.123")
    }
    var irPortText by remember {
        mutableStateOf(preferences.getInt("ir_port", 8081).toString())
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

    var showDebugOverlayState by remember {
        mutableStateOf(showDebugOverlay)
    }

    val isLoaded = pokeWalker != null

    val controlWidth = 140.dp

    // Lightest color of each tint palette, used for the bezel directly behind the LCD
    val lcdBackground = tintLcdBackground(selectedTint)

    val sidebarWidth = 260.dp
    val sidebarOffsetX by animateDpAsState(
        targetValue = if (sidebarOpen) 0.dp else -sidebarWidth,
        label = "sidebarOffsetX"
    )

    LaunchedEffect(pokeWalker) {
        if (pokeWalker != null) {
            pokeWalker.setDisableSleep(disableSleepCheatEnabled)
        }
    }

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
            BallThemeBackground(
                selectedBallTheme = selectedBallTheme,
                bitmaps = ballBackgroundBitmaps,
                modifier = Modifier.fillMaxSize()
            )

            PWLcd(
                canvasBitmap = canvasBitmap,
                lcdBackground = lcdBackground,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 16.dp)
            )

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
        val sidebarBackground = tintSidebarBackground(selectedTint)

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

                FilesSection(
                    filesExpanded = filesExpanded,
                    onFilesExpandedChange = { filesExpanded = !filesExpanded },
                    isLoaded = isLoaded,
                    onLoadRom = onLoadRom,
                    onLoadEeprom = onLoadEeprom
                )

                AppearanceSection(
                    appearanceExpanded = appearanceExpanded,
                    onAppearanceExpandedChange = { appearanceExpanded = !appearanceExpanded },
                    selectedBallTheme = selectedBallTheme,
                    onSelectedBallThemeChange = { theme ->
                        selectedBallTheme = theme
                        preferences.edit().putString("ball_theme", theme.name).apply()
                    },
                    ballIconBitmaps = ballIconBitmaps,
                    selectedTint = selectedTint,
                    onTintSelected = { tint ->
                        selectedTint = tint
                        onTintSelected(tint)
                        preferences.edit().putString("tint", tint.name).apply()
                    },
                    colorizationEnabled = colorizationEnabled,
                    onColorizationEnabledChange = { checked ->
                        colorizationEnabled = checked
                        preferences.edit().putBoolean("colorization_enabled", checked).apply()
                        pokeWalker?.setColorMode(checked)
                    },
                    selectedShader = selectedShader,
                    onSelectedShaderChange = { option ->
                        selectedShader = option
                        preferences.edit().putString("shader_option", option.name).apply()
                    },
                    shadeLevel = shadeLevel,
                    onShadeLevelChange = { level ->
                        shadeLevel = level
                        preferences.edit().putInt("shade_level", level).apply()
                    },
                    selectedLcdSize = selectedLcdSize,
                    onSelectedLcdSizeChange = { size ->
                        selectedLcdSize = size
                    },
                    controlWidth = controlWidth,
                    dropdownBackground = dropdownBackground
                )

                SoundSection(
                    soundExpanded = soundExpanded,
                    onSoundExpandedChange = { soundExpanded = !soundExpanded },
                    softChirpEnabled = softChirpEnabled,
                    onSoftChirpEnabledChange = { checked ->
                        softChirpEnabled = checked
                        preferences.edit().putBoolean("soft_chirp_enabled", checked).apply()
                    },
                    volumeLevel = volumeLevel,
                    onVolumeLevelChange = { level ->
                        volumeLevel = level
                        preferences.edit().putInt("volume_level", level).apply()
                    },
                    hapticsStrength = hapticsStrength,
                    onHapticsStrengthChange = { level ->
                        hapticsStrength = level
                        preferences.edit().putInt("haptics_strength", level).apply()
                    },
                    controlWidth = controlWidth
                )

                TweaksSection(
                    cheatsExpanded = cheatsExpanded,
                    onCheatsExpandedChange = { cheatsExpanded = !cheatsExpanded },
                    debugExpanded = debugExpanded,
                    onDebugExpandedChange = { debugExpanded = !debugExpanded },
                    disableSleepCheatEnabled = disableSleepCheatEnabled,
                    onDisableSleepCheatChange = { checked ->
                        disableSleepCheatEnabled = checked
                        preferences.edit().putBoolean("cheat_disable_sleep", checked).apply()
                        pokeWalker?.setDisableSleep(checked)
                    },
                    onAdjustWatts = { delta ->
                        pokeWalker?.adjustWatts(delta)
                    },
                    irEnabled = irEnabled,
                    onIrEnabledChange = { checked ->
                        irEnabled = checked
                        preferences.edit().putBoolean("ir_enabled", checked).apply()
                    },
                    irHost = irHost,
                    onIrHostChange = { value ->
                        irHost = value
                        preferences.edit().putString("ir_host", value).apply()
                    },
                    irPortText = irPortText,
                    onIrPortTextChange = { value ->
                        irPortText = value
                        val parsed = value.toIntOrNull() ?: 8081
                        preferences.edit().putInt("ir_port", parsed).apply()
                    },
                    showDebugOverlay = showDebugOverlayState,
                    onShowDebugOverlayChange = { enabled ->
                        showDebugOverlayState = enabled
                        onShowDebugOverlayChange(enabled)
                    },
                    controlWidth = controlWidth
                )
            }
        }
    }
}