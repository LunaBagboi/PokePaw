import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

class AudioEngine {
    companion object {
        private const val AUDIO_RENDER_FREQUENCY = 256
        private const val SAMPLE_RATE = 44100
        private const val BASE_AMPLITUDE = 32768 / 2
        private const val TARGET_LATENCY = 10
        private const val MIN_FREQUENCY = 100
        private const val MAX_FREQUENCY = 20000
    }

    private var audioTrack: AudioTrack? = null
    private var currentPhase = 0.0f
    private var lastFreq = 0.0f
    private var isInitialized = false

    init {
        initializeAudio()
    }

    private fun initializeAudio() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(maxOf(bufferSize, 1024 * 2)) // Ensure minimum buffer
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack?.play() // Start playback (equivalent to SDL_PauseAudioDevice(audioDevice, 0))
                isInitialized = true
            } else {
                println("Failed to initialize AudioTrack")
            }
        } catch (e: Exception) {
            println("Failed to open audio device: ${e.message}")
        }
    }

    fun close() {
        audioTrack?.let { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.stop()
                track.release()
            }
        }
        audioTrack = null
        isInitialized = false
    }

    fun render(frequency: Float, volumeMultiplier: Float = 1.0f, speed: Float = 1.0f) {
        if (!isInitialized || audioTrack == null) return

        if (frequency != lastFreq) {
            lastFreq = frequency
            currentPhase = 0.0f
        }

        var sampleCount = ceil(SAMPLE_RATE / (AUDIO_RENDER_FREQUENCY * speed)).toInt()

        // Latency management (equivalent to SDL_GetQueuedAudioSize)
        val playbackHeadPosition = audioTrack!!.playbackHeadPosition
        val writePosition = audioTrack!!.playbackHeadPosition
        val queuedSamples = writePosition - playbackHeadPosition
        val latency = (queuedSamples.toFloat()) / (SAMPLE_RATE / 1000.0f)

        when {
            latency > TARGET_LATENCY -> {
                sampleCount = maxOf(1, sampleCount - 1)
            }
            latency < TARGET_LATENCY / 2.0f -> {
                sampleCount += 1
            }
        }

        val audioBuffer = ShortArray(sampleCount)
        val targetAmplitude = BASE_AMPLITUDE * volumeMultiplier

        if (frequency >= MIN_FREQUENCY && frequency <= MAX_FREQUENCY) {
            val samplesPerCycle = SAMPLE_RATE / frequency
            val nyquistFreq = SAMPLE_RATE / 2.0f
            val maxHarmonic = floor(nyquistFreq / frequency).toInt()

            for (i in 0 until sampleCount) {
                val time = (currentPhase + i) / SAMPLE_RATE
                var sample = 0.0f

                // Generate square wave using odd harmonics (exactly matching original)
                var h = 1
                while (h <= maxHarmonic) {
                    sample += sin(2.0f * PI.toFloat() * frequency * h * time) / h
                    h += 2
                }

                sample = (sample * 4.0f / PI.toFloat()) * targetAmplitude
                audioBuffer[i] = sample.coerceIn(-32768.0f, 32767.0f).toInt().toShort()
            }

            currentPhase += sampleCount
            currentPhase %= samplesPerCycle
        } else {
            // Fill with silence for out-of-range frequencies
            audioBuffer.fill(0)
        }

        // Write audio data (equivalent to SDL_QueueAudio)
        audioTrack?.write(audioBuffer, 0, audioBuffer.size)
    }
}