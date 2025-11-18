package com.halfheart.pocketwalkerlib

const val BUTTON_CENTER = 1 shl 0
const val BUTTON_LEFT = 1 shl 2
const val BUTTON_RIGHT = 1 shl 4

class PocketWalkerNative {

    external fun create(romBytes: ByteArray, eepromBytes: ByteArray)
    external fun start()
    external fun stop()
    external fun pause()
    external fun resume()

    external fun onDraw(callback: (ByteArray) -> Unit)
    external fun onAudio(callback: (Float, Boolean) -> Unit)

    external fun onTransmitSci3(callback: (Byte) -> Unit)
    external fun receiveSci3(byte: Byte)

    external fun press(button: Int)
    external fun release(button: Int)

    external fun getEepromBuffer(): ByteArray
    external fun getContrast(): Byte


    external fun setAccelerationData(x: Float, y: Float, z: Float)

    external fun setDisableSleep(disable: Boolean)

    external fun setColorMode(enabled: Boolean)

    external fun getColorFrame(): IntArray

    external fun getWalkerDexNumber(): Int

    external fun getWalkerVariantInfo(): IntArray

    external fun getCurrentRouteId(): Int

    external fun isSpecialRoute(): Boolean

    external fun setColorSprite(id: String, pixels: IntArray, width: Int, height: Int)

    external fun getCurrentWatts(): Int

    companion object {
        init {
            System.loadLibrary("pocketwalkerlib")
        }
    }
}