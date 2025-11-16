#include <jni.h>
#include "PocketWalkerState.h"
#include "KotlinCallback.h"
#include <android/log.h>

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
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_start(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();

    emulator->StartSync();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_onDraw(JNIEnv *env, jobject thiz,
                                                             jobject callback) {
    auto emulator = PocketWalkerState::Emulator();

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

    emulator->PressButton((Buttons::Button) (uint8_t) button);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_release(JNIEnv *env, jobject thiz,
                                                              jint button) {
    auto emulator = PocketWalkerState::Emulator();

    emulator->ReleaseButton((Buttons::Button) (uint8_t) button);
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_getEepromBuffer(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();

    uint8_t* eepromData = emulator->GetEepromBuffer();
    size_t eepromSize = 0xFFFF;

    jbyteArray byteArray = env->NewByteArray(static_cast<jsize>(eepromSize));
    env->SetByteArrayRegion(byteArray, 0, static_cast<jsize>(eepromSize),
                            reinterpret_cast<const jbyte*>(eepromData));

    return byteArray;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_stop(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();

    emulator->Stop();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_pause(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();

    emulator->Pause();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_resume(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();

    emulator->Resume();
}
extern "C"
JNIEXPORT jbyte JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_getContrast(JNIEnv *env, jobject thiz) {
    auto emulator = PocketWalkerState::Emulator();

    return (jbyte) emulator->GetContrast();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_onTransmitSci3(JNIEnv *env, jobject thiz,
                                                                     jobject callback) {
    auto emulator = PocketWalkerState::Emulator();

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

    emulator->ReceiveSci3((uint8_t) byte);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_halfheart_pocketwalkerlib_PocketWalkerNative_setAccelerationData(JNIEnv *env, jobject thiz,
                                                                          jfloat x, jfloat y,
                                                                          jfloat z) {
    auto emulator = PocketWalkerState::Emulator();

    //emulator->SetAccelerationData(x, y, z);
}