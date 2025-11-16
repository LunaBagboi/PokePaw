#include <jni.h>
#include <functional>

class KotlinCallback {
private:
    static JavaVM* g_jvm;

    static bool GetJNIEnv(JNIEnv** env, bool* shouldDetach) {
        *shouldDetach = false;

        if (!g_jvm) return false;

        int getEnvStat = g_jvm->GetEnv(reinterpret_cast<void**>(env), JNI_VERSION_1_6);
        if (getEnvStat == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(env, nullptr) != 0) {
                return false; // Failed to attach
            }
            *shouldDetach = true;
        }
        return getEnvStat == JNI_OK || getEnvStat == JNI_EDETACHED;
    }

    static void CleanupJNIEnv(bool shouldDetach) {
        if (shouldDetach && g_jvm) {
            g_jvm->DetachCurrentThread();
        }
    }

public:
    static void Initialize(JavaVM* vm) {
        g_jvm = vm;
    }

    // Value converter functions
    static std::function<jobject(JNIEnv*, float)> FloatConverter() {
        return [](JNIEnv* env, float value) -> jobject {
            jclass floatClass = env->FindClass("java/lang/Float");
            jmethodID floatConstructor = env->GetMethodID(floatClass, "<init>", "(F)V");
            jobject floatObject = env->NewObject(floatClass, floatConstructor, value);
            env->DeleteLocalRef(floatClass);
            return floatObject;
        };
    }

    static std::function<jobject(JNIEnv*, uint8_t)> ByteConverter() {
        return [](JNIEnv* env, uint8_t value) -> jobject {
            jclass byteClass = env->FindClass("java/lang/Byte");
            jmethodID byteConstructor = env->GetMethodID(byteClass, "<init>", "(B)V");

            jbyte signedByte = static_cast<jbyte>(value);

            jobject byteObject = env->NewObject(byteClass, byteConstructor, signedByte);
            env->DeleteLocalRef(byteClass);
            return byteObject;
        };
    }

    static std::function<jobject(JNIEnv*, int)> IntConverter() {
        return [](JNIEnv* env, int value) -> jobject {
            jclass intClass = env->FindClass("java/lang/Integer");
            jmethodID intConstructor = env->GetMethodID(intClass, "<init>", "(I)V");
            jobject intObject = env->NewObject(intClass, intConstructor, value);
            env->DeleteLocalRef(intClass);
            return intObject;
        };
    }

    static std::function<jobject(JNIEnv*, const char*)> StringConverter() {
        return [](JNIEnv* env, const char* value) -> jobject {
            return env->NewStringUTF(value);
        };
    }

    static std::function<jobject(JNIEnv*, bool)> BoolConverter() {
        return [](JNIEnv* env, bool value) -> jobject {
            jclass booleanClass = env->FindClass("java/lang/Boolean");
            jmethodID booleanConstructor = env->GetMethodID(booleanClass, "<init>", "(Z)V");
            jobject booleanObject = env->NewObject(booleanClass, booleanConstructor, value);
            env->DeleteLocalRef(booleanClass);
            return booleanObject;
        };
    }

    static std::function<jobject(JNIEnv*, double)> DoubleConverter() {
        return [](JNIEnv* env, double value) -> jobject {
            jclass doubleClass = env->FindClass("java/lang/Double");
            jmethodID doubleConstructor = env->GetMethodID(doubleClass, "<init>", "(D)V");
            jobject doubleObject = env->NewObject(doubleClass, doubleConstructor, value);
            env->DeleteLocalRef(doubleClass);
            return doubleObject;
        };
    }

    static std::function<jobject(JNIEnv*, long)> LongConverter() {
        return [](JNIEnv* env, long value) -> jobject {
            jclass longClass = env->FindClass("java/lang/Long");
            jmethodID longConstructor = env->GetMethodID(longClass, "<init>", "(J)V");
            jobject longObject = env->NewObject(longClass, longConstructor, value);
            env->DeleteLocalRef(longClass);
            return longObject;
        };
    }

    static std::function<jobject(JNIEnv*, uint8_t*)> ByteArrayConverter(jsize size) {
        return [size](JNIEnv* env, uint8_t* data) -> jobject {
            jbyteArray javaByteArray = env->NewByteArray(size);
            env->SetByteArrayRegion(javaByteArray, 0, size, reinterpret_cast<const jbyte*>(data));
            return javaByteArray;
        };
    }

    // Core template method
    template<typename T>
    static void InvokeCallback(jobject callback, T value,
                               std::function<jobject(JNIEnv*, T)> valueConverter) {
        if (!g_jvm || !callback) return;

        JNIEnv* env;
        bool shouldDetach = false;

        if (!GetJNIEnv(&env, &shouldDetach)) return;

        try {
            jobject javaValue = valueConverter(env, value);

            jclass callbackClass = env->GetObjectClass(callback);
            jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");

            env->CallObjectMethod(callback, invokeMethod, javaValue);

            env->DeleteLocalRef(javaValue);
            env->DeleteLocalRef(callbackClass);

        } catch (...) {
        }

        CleanupJNIEnv(shouldDetach);
    }

    // Simplified convenience methods using extracted converters
    static void InvokeByteArrayCallback(jobject callback, uint8_t* data, jsize size) {
        InvokeCallback<uint8_t*>(callback, data, ByteArrayConverter(size));
    }

    static void InvokeFloatCallback(jobject callback, float value) {
        InvokeCallback<float>(callback, value, FloatConverter());
    }

    static void InvokeByteCallback(jobject callback, uint8_t value) {
        InvokeCallback<uint8_t>(callback, value, ByteConverter());
    }

    static void InvokeIntCallback(jobject callback, int value) {
        InvokeCallback<int>(callback, value, IntConverter());
    }

    static void InvokeStringCallback(jobject callback, const char* value) {
        InvokeCallback<const char*>(callback, value, StringConverter());
    }

    static void InvokeBoolCallback(jobject callback, bool value) {
        InvokeCallback<bool>(callback, value, BoolConverter());
    }

    static void InvokeDoubleCallback(jobject callback, double value) {
        InvokeCallback<double>(callback, value, DoubleConverter());
    }

    static void InvokeLongCallback(jobject callback, long value) {
        InvokeCallback<long>(callback, value, LongConverter());
    }

    static void InvokeVoidCallback(jobject callback) {
        if (!g_jvm || !callback) return;

        JNIEnv* env;
        bool shouldDetach = false;

        if (!GetJNIEnv(&env, &shouldDetach)) return;

        try {
            jclass callbackClass = env->GetObjectClass(callback);
            jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke", "()Ljava/lang/Object;");

            env->CallObjectMethod(callback, invokeMethod);

            env->DeleteLocalRef(callbackClass);

        } catch (...) {
        }

        CleanupJNIEnv(shouldDetach);
    }

    template<typename T1, typename T2>
    static void InvokeFunction2Callback(jobject callback, T1 first, T2 second,
                                        std::function<jobject(JNIEnv*, T1)> firstConverter,
                                        std::function<jobject(JNIEnv*, T2)> secondConverter) {
        if (!g_jvm || !callback) return;

        JNIEnv *env;
        bool shouldDetach = false;
        if (!GetJNIEnv(&env, &shouldDetach)) return;

        try {
            jobject firstObj = firstConverter(env, first);
            jobject secondObj = secondConverter(env, second);

            jclass callbackClass = env->GetObjectClass(callback);
\
            jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke",
                                                      "(Ljava/lang/Object;Ljava/lang/Object;)V");

            if (invokeMethod) {
                env->CallVoidMethod(callback, invokeMethod, firstObj, secondObj);
            } else {
                env->ExceptionClear();
                invokeMethod = env->GetMethodID(callbackClass, "invoke",
                                                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
                if (invokeMethod) {
                    jobject result = env->CallObjectMethod(callback, invokeMethod, firstObj, secondObj);
                    if (result) env->DeleteLocalRef(result);
                }
            }

            env->DeleteLocalRef(firstObj);
            env->DeleteLocalRef(secondObj);
            env->DeleteLocalRef(callbackClass);

        } catch (...) {}

        CleanupJNIEnv(shouldDetach);
    }
};

JavaVM* KotlinCallback::g_jvm = nullptr;