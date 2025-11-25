#include <jni.h>
#include "PocketWalkerState.h"
#include "KotlinCallback.h"
#include <android/log.h>
#include "SleepConfig.h"

class CallbackManager {
private:
    static CallbackManager* instance;

    std::map<std::string, jobject> callbacks;

public:
    static CallbackManager& Instance() {
        if (!instance) {
            instance = new CallbackManager();
        }
        return *instance;
    }

    void SetCallback(JNIEnv* env, jobject callback, std::string name) {
        callbacks[name] = callback ? env->NewGlobalRef(callback) : nullptr;
    }

    jobject GetCallback( std::string name) {
        return callbacks[name];
    }

};

CallbackManager* CallbackManager::instance = nullptr;

// Global flag indicating whether the emulator should use the experimental color display mode.
// This is toggled from Kotlin via setColorMode and can be read by native rendering components.
static bool gColorModeEnabled = false;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    KotlinCallback::Initialize(vm);
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_create(JNIEnv *env, jobject thiz,
                                                             jbyteArray rom_bytes,
                                                             jbyteArray eeprom_bytes) {
    jsize romSize = env->GetArrayLength(rom_bytes);
    jsize eepromSize = env->GetArrayLength(eeprom_bytes);

    jbyte* romBuffer = env->GetByteArrayElements(rom_bytes, nullptr);
    jbyte* eepromBuffer = env->GetByteArrayElements(eeprom_bytes, nullptr);

    if (!romBuffer || !eepromBuffer) {
        if (romBuffer) env->ReleaseByteArrayElements(rom_bytes, romBuffer, JNI_ABORT);
        if (eepromBuffer) env->ReleaseByteArrayElements(eeprom_bytes, eepromBuffer, JNI_ABORT);
        return;
    }

    auto fullRomBuffer = std::make_unique<std::array<uint8_t, 0xFFFF>>();
    auto fullEepromBuffer = std::make_unique<std::array<uint8_t, 0xFFFF>>();

    fullRomBuffer->fill(0);
    fullEepromBuffer->fill(0);

    size_t romCopySize = std::min(static_cast<size_t>(romSize), fullRomBuffer->size());
    std::copy(romBuffer, romBuffer + romCopySize, fullRomBuffer->begin());

    size_t eepromCopySize = std::min(static_cast<size_t>(eepromSize), fullEepromBuffer->size());
    std::copy(eepromBuffer, eepromBuffer + eepromCopySize, fullEepromBuffer->begin());

    env->ReleaseByteArrayElements(rom_bytes, romBuffer, JNI_ABORT);
    env->ReleaseByteArrayElements(eeprom_bytes, eepromBuffer, JNI_ABORT);

    uint8_t* persistentRom = new uint8_t[0xFFFF];
    uint8_t* persistentEeprom = new uint8_t[0xFFFF];

    std::copy(fullRomBuffer->begin(), fullRomBuffer->end(), persistentRom);
    std::copy(fullEepromBuffer->begin(), fullEepromBuffer->end(), persistentEeprom);

    auto emulator = new PokeWalker(persistentRom, persistentEeprom);
    emulator->SetExceptionHandling(false);
    PocketWalkerState::SetEmulator(emulator);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_setColorMode(JNIEnv *env, jobject thiz,
                                                                   jboolean enabled) {
    gColorModeEnabled = (enabled == JNI_TRUE);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_start(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    emulator->StartSync();
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_getColorFrame(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return nullptr;
    }

    const auto &buffer = emulator->GetColorBuffer();

    const jsize size = static_cast<jsize>(buffer.size());
    jintArray result = env->NewIntArray(size);
    if (!result) {
        return nullptr;
    }

    env->SetIntArrayRegion(result, 0, size, reinterpret_cast<const jint*>(buffer.data()));
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_onDraw(JNIEnv *env, jobject thiz,
                                                             jobject callback) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    CallbackManager::Instance().SetCallback(env, callback, "Draw");

    emulator->OnDraw([](uint8_t* data) {
        auto size = Lcd::WIDTH * Lcd::HEIGHT;
        jobject drawCallback = CallbackManager::Instance().GetCallback("Draw");
        if (drawCallback) {
            KotlinCallback::InvokeByteArrayCallback(drawCallback, data, size);
        }
    });
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_onAudio(JNIEnv *env, jobject thiz,
                                                              jobject callback) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    CallbackManager::Instance().SetCallback(env, callback, "Audio");

    emulator->OnAudio([](AudioInformation audio) {
        jobject audioCallback = CallbackManager::Instance().GetCallback("Audio");
        if (audioCallback) {
            KotlinCallback::InvokeFunction2Callback<float, bool>(audioCallback, audio.frequency, audio.isFullVolume, KotlinCallback::FloatConverter(), KotlinCallback::BoolConverter());
        }
    });
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_press(JNIEnv *env, jobject thiz,
                                                            jint button) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    emulator->PressButton((Buttons::Button) (uint8_t) button);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_release(JNIEnv *env, jobject thiz,
                                                              jint button) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    emulator->ReleaseButton((Buttons::Button) (uint8_t) button);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_getEepromBuffer(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return nullptr;
    }

    uint8_t* eepromData = emulator->GetEepromBuffer();

    size_t eepromSize = 0xFFFF;

    jbyteArray byteArray = env->NewByteArray(static_cast<jsize>(eepromSize));
    env->SetByteArrayRegion(byteArray, 0, static_cast<jsize>(eepromSize),
                            reinterpret_cast<const jbyte*>(eepromData));

    return byteArray;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_getWalkerDexNumber(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return 0;
    }

    return static_cast<jint>(emulator->GetWalkerDexNumber());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_getCurrentWatts(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return 0;
    }

    return static_cast<jint>(emulator->GetCurrentWatts());
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_adjustWatts(JNIEnv *env, jobject thiz,
                                                                  jint delta) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    emulator->AdjustWatts(static_cast<int16_t>(delta));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_addFusedSteps(JNIEnv *env, jobject thiz,
                                                                    jint count) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator || count <= 0) {
        return;
    }

    emulator->AddFusedSteps(static_cast<uint16_t>(count));
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_getAccelWindow(JNIEnv *env, jobject thiz,
                                                                     jint start,
                                                                     jint length) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator || length <= 0) {
        return env->NewByteArray(0);
    }

    if (start < 0) {
        start = 0;
    }
    if (length > 0x7F) {
        length = 0x7F;
    }

    std::vector<uint8_t> buffer(static_cast<size_t>(length));
    emulator->ReadAccelerometerWindow(static_cast<uint8_t>(start),
                                      static_cast<uint8_t>(length),
                                      buffer.data());

    jbyteArray result = env->NewByteArray(length);
    if (!result) {
        return nullptr;
    }
    env->SetByteArrayRegion(result, 0, length,
                            reinterpret_cast<const jbyte*>(buffer.data()));
    return result;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_getWalkerVariantInfo(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return nullptr;
    }

    const auto info = emulator->GetWalkerVariantInfo();

    jintArray result = env->NewIntArray(5);
    if (!result) {
        return nullptr;
    }

    jint values[5];
    values[0] = static_cast<jint>(info.species);
    values[1] = static_cast<jint>(info.variant);
    values[2] = info.isFemale ? 1 : 0;
    values[3] = info.isShiny ? 1 : 0;
    values[4] = info.hasForm ? 1 : 0;

    env->SetIntArrayRegion(result, 0, 5, values);
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_setColorSprite(JNIEnv *env, jobject thiz,
                                                                     jstring jId,
                                                                     jintArray pixels,
                                                                     jint width,
                                                                     jint height) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator || !jId || !pixels || width <= 0 || height <= 0) {
        return;
    }

    const char* idChars = env->GetStringUTFChars(jId, nullptr);
    if (!idChars) {
        return;
    }

    std::string id(idChars);
    env->ReleaseStringUTFChars(jId, idChars);

    const jsize length = env->GetArrayLength(pixels);
    if (length <= 0) {
        return;
    }

    std::vector<uint32_t> localPixels(static_cast<size_t>(length));
    env->GetIntArrayRegion(pixels, 0, length,
                           reinterpret_cast<jint*>(localPixels.data()));

    emulator->SetColorSprite(id,
                             localPixels.data(),
                             localPixels.size(),
                             static_cast<uint8_t>(width),
                             static_cast<uint8_t>(height));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_stop(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    emulator->Stop();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_getCurrentRouteId(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return 0;
    }

    return (jint) emulator->GetCurrentRouteId();
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_isSpecialRoute(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return JNI_FALSE;
    }

    return (jboolean) (emulator->IsSpecialRoute() ? JNI_TRUE : JNI_FALSE);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_pause(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    emulator->Pause();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_resume(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    emulator->Resume();
}

extern "C"
JNIEXPORT jbyte JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_getContrast(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return 0;
    }

    return (jbyte) emulator->GetContrast();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_onTransmitSci3(JNIEnv *env, jobject thiz,
                                                                     jobject callback) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    CallbackManager::Instance().SetCallback(env, callback, "TransmitSCI3");

    emulator->OnTransmitSci3([](uint8_t byte) {
        jobject transmitCallback = CallbackManager::Instance().GetCallback("TransmitSCI3");
        if (transmitCallback) {
            jbyte signedByte = static_cast<jbyte>(static_cast<int8_t>(byte));
            KotlinCallback::InvokeByteCallback(transmitCallback, signedByte);
        }
    });
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_receiveSci3(JNIEnv *env, jobject thiz,
                                                                  jbyte byte) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    emulator->ReceiveSci3((uint8_t) byte);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_setAccelerationData(JNIEnv *env, jobject thiz,
                                                                          jfloat x, jfloat y,
                                                                          jfloat z) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    emulator->SetAccelerationData(x, y, z);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_setDisableSleep(JNIEnv *env, jobject thiz,
                                                                      jboolean disable) {
    g_disableSleep = (disable == JNI_TRUE);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_setWalkerShinyCheat(JNIEnv *env, jobject thiz,
                                                                          jboolean shiny) {
    auto emulator = PocketWalkerState::Emulator();
    if (!emulator) {
        return;
    }

    emulator->SetWalkerShinyCheat(shiny == JNI_TRUE);
}