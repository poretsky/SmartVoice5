// SVOX Pico TTS engine java native interface

#include <cstdlib>
#include <cstring>

#ifdef USE_LOWSHELF_FILTER
#include <cmath>
#endif

#include <jni_bootstrap.hpp>

#include "TtsEngine.h"


#ifdef USE_LOWSHELF_FILTER
// EQ + BOOST parameters
#define FILTER_LOWSHELF_ATTENUATION -18.0f // in dB
#define FILTER_TRANSITION_FREQ 1100.0f     // in Hz
#define FILTER_SHELF_SLOPE 1.0f            // Q
#define FILTER_GAIN 5.5f // linear gain
#define DEFAULT_TTS_RATE        16000
#endif


using namespace android;


// Local classes

// Callback interface
class cb_handler_t
{
public:
    cb_handler_t(JNIEnv* cb_env, jobject cb_obj, jclass cls):
        env(cb_env),
        obj(cb_obj)
    {
        method = env->GetMethodID(cls, "outputAudioStream", "([B)Z");
    }

    bool executeCallback(int8_t *buffer, size_t bufferSize)
    {
        jbyteArray data = env->NewByteArray(bufferSize);
        env->SetByteArrayRegion(data, 0, bufferSize, reinterpret_cast<jbyte*>(buffer));
        return static_cast<bool>(env->CallBooleanMethod(obj, method, data));
    }

private:
    JNIEnv* env;
    jobject obj;
    jmethodID method;
};

// Simplified auto_ptr functionality
class auto_buffer_t
{
public:
    auto_buffer_t(size_t size):
        length(size)
    {
        if (size)
            content = new int8_t[size];
        else content = 0;
    }

    ~auto_buffer_t(void)
    {
        if (content)
            delete[] content;
    }

    size_t length;
    int8_t* content;
};


#ifdef USE_LOWSHELF_FILTER

// EQ data

static double m_fa, m_fb, m_fc, m_fd, m_fe;
static double x0;  // x[n]
static double x1;  // x[n-1]
static double x2;  // x[n-2]
static double out0;// y[n]
static double out1;// y[n-1]
static double out2;// y[n-2]

static float fFilterLowshelfAttenuation = FILTER_LOWSHELF_ATTENUATION;
static float fFilterTransitionFreq = FILTER_TRANSITION_FREQ;
static float fFilterShelfSlope = FILTER_SHELF_SLOPE;
static float fFilterGain = FILTER_GAIN;

#endif


// Local functions

#ifdef USE_LOWSHELF_FILTER
static void
initializeEQ()
{
    double amp = float(pow(10.0, fFilterLowshelfAttenuation / 40.0));
    double w = 2.0 * M_PI * (fFilterTransitionFreq / DEFAULT_TTS_RATE);
    double sinw = float(sin(w));
    double cosw = float(cos(w));
    double beta = float(sqrt(amp)/fFilterShelfSlope);

    // initialize low-shelf parameters
    double b0 = amp * ((amp+1.0F) - ((amp-1.0F)*cosw) + (beta*sinw));
    double b1 = 2.0F * amp * ((amp-1.0F) - ((amp+1.0F)*cosw));
    double b2 = amp * ((amp+1.0F) - ((amp-1.0F)*cosw) - (beta*sinw));
    double a0 = (amp+1.0F) + ((amp-1.0F)*cosw) + (beta*sinw);
    double a1 = 2.0F * ((amp-1.0F) + ((amp+1.0F)*cosw));
    double a2 = -((amp+1.0F) + ((amp-1.0F)*cosw) - (beta*sinw));

    m_fa = fFilterGain * b0/a0;
    m_fb = fFilterGain * b1/a0;
    m_fc = fFilterGain * b2/a0;
    m_fd = a1/a0;
    m_fe = a2/a0;
}

static void
initializeFilter()
{
    x0 = 0.0f;
    x1 = 0.0f;
    x2 = 0.0f;
    out0 = 0.0f;
    out1 = 0.0f;
    out2 = 0.0f;
}

static void
applyFilter(int16_t* buffer, size_t sampleCount)
{
    for (size_t i=0 ; i<sampleCount ; i++)
    {
        x0 = (double) buffer[i];
        out0 = (m_fa*x0) + (m_fb*x1) + (m_fc*x2) + (m_fd*out1) + (m_fe*out2);
        x2 = x1;
        x1 = x0;
        out2 = out1;
        out1 = out0;
        if (out0 > 32767.0f)
            buffer[i] = 32767;
        else if (out0 < -32768.0f)
            buffer[i] = -32768;
        else buffer[i] = (int16_t) out0;
    }
}
#endif

// Get TTS engine referenced by object
static TtsEngine*
getEngine(JNIEnv* env, jobject obj)
{
    jclass cls = env->GetObjectClass(obj);
    jlong jniData = env->GetLongField(obj, env->GetFieldID(cls, "jniData", "J"));
    return jniData ? reinterpret_cast<TtsEngine*>(jniData) : NULL;
}

// Transfer control to the Java callback
static tts_callback_status
ttsSynthDoneCB(void *&userData, uint32_t sampleRate, tts_audio_format format, int nChannels, int8_t *&buffer, size_t &bufferSize, tts_synth_status status)
{
    (void) sampleRate;
    (void) format;
    (void) nChannels;

    tts_callback_status result = ((status != TTS_SYNTH_DONE) && userData && buffer && bufferSize) ?
        TTS_CALLBACK_CONTINUE :
        TTS_CALLBACK_HALT;

    if (result == TTS_CALLBACK_CONTINUE)
    {
#ifdef USE_LOWSHELF_FILTER
        applyFilter(reinterpret_cast<int16_t*>(buffer), bufferSize / 2);
#endif
        result = reinterpret_cast<cb_handler_t*>(userData)->executeCallback(buffer, bufferSize) ?
            TTS_CALLBACK_HALT :
            TTS_CALLBACK_CONTINUE;
    }

    return result;
}


// Exported native methods

static jlong
initialize(JNIEnv* env, jobject obj, jstring resourcePath)
{
    TtsEngine* ttsEngine = getTtsEngine();
    const char* resourcePathNativeString = env->GetStringUTFChars(resourcePath, 0);
    jlong result = (ttsEngine->init(ttsSynthDoneCB, resourcePathNativeString) == TTS_SUCCESS) ?
        reinterpret_cast<jlong>(ttsEngine) :
        0;
    env->ReleaseStringUTFChars(resourcePath, resourcePathNativeString);
#ifdef USE_LOWSHELF_FILTER
    if (result)
        initializeEQ();
#endif
    return result;
}

static void
shutdown(JNIEnv* env, jobject obj)
{
    TtsEngine* engine = getEngine(env, obj);
    if (engine)
        engine->shutdown();
}

static jint
hasVoiceFor(JNIEnv* env, jobject obj, jstring lang, jstring country, jstring variant)
{
    TtsEngine* engine = getEngine(env, obj);
    jint result = TTS_LANG_NOT_SUPPORTED;
    if (engine)
    {
        const char* langNativeString = env->GetStringUTFChars(lang, 0);
        const char* countryNativeString = env->GetStringUTFChars(country, 0);
        const char* variantNativeString = env->GetStringUTFChars(variant, 0);
        result = engine->isLanguageAvailable(langNativeString, countryNativeString, variantNativeString);
        env->ReleaseStringUTFChars(lang, langNativeString);
        env->ReleaseStringUTFChars(country, countryNativeString);
        env->ReleaseStringUTFChars(variant, variantNativeString);
    }
    return result;
}

static jint
setLanguage(JNIEnv* env, jobject obj, jstring lang, jstring country, jstring variant)
{
    TtsEngine* engine = getEngine(env, obj);
    jint result = TTS_LANG_NOT_SUPPORTED;
    if (engine)
    {
        const char* langNativeString = env->GetStringUTFChars(lang, 0);
        const char* countryNativeString = env->GetStringUTFChars(country, 0);
        const char* variantNativeString = env->GetStringUTFChars(variant, 0);
        result = engine->setLanguage(langNativeString, countryNativeString, variantNativeString);
        env->ReleaseStringUTFChars(lang, langNativeString);
        env->ReleaseStringUTFChars(country, countryNativeString);
        env->ReleaseStringUTFChars(variant, variantNativeString);
    }
    return result;
}

static jobjectArray
getCurrentLanguage(JNIEnv* env, jobject obj)
{
    TtsEngine* engine = getEngine(env, obj);
    jobjectArray result = NULL;
    if (engine)
    {
        size_t bufSize = 100;
        char lang[bufSize];
        char country[bufSize];
        char variant[bufSize];
        memset(lang, 0, bufSize);
        memset(country, 0, bufSize);
        memset(variant, 0, bufSize);
        result = env->NewObjectArray(3, env->FindClass("java/lang/String"), env->NewStringUTF(""));
        engine->getLanguage(lang, country, variant);
        env->SetObjectArrayElement(result, 0, env->NewStringUTF(lang));
        env->SetObjectArrayElement(result, 1, env->NewStringUTF(country));
        env->SetObjectArrayElement(result, 2, env->NewStringUTF(variant));
    }
    return result;
}

static jint
setProperty(JNIEnv* env, jobject obj, jstring name, jstring value)
{
    TtsEngine* engine = getEngine(env, obj);
    jint result = TTS_FAILURE;
    if (engine)
    {
        const char* nameNativeString = env->GetStringUTFChars(name, 0);
        const char* valueNativeString = env->GetStringUTFChars(value, 0);
        size_t valueLength = env->GetStringUTFLength(value);
        result = engine->setProperty(nameNativeString, valueNativeString, valueLength);
        env->ReleaseStringUTFChars(name, nameNativeString);
        env->ReleaseStringUTFChars(value, valueNativeString);
    }
    return result;
}

static jint
speak(JNIEnv* env, jobject obj, jstring text)
{
    TtsEngine* engine = getEngine(env, obj);
    jint result = TTS_FAILURE;
    if (engine)
    {
        jclass cls = env->GetObjectClass(obj);
        const char* utfText = env->GetStringUTFChars(text, 0);
        cb_handler_t cb_handler(env, obj, cls);
        auto_buffer_t audiobuffer(static_cast<size_t>(env->GetIntField(obj, env->GetFieldID(cls, "audioBufferSize", "I"))));
#ifdef USE_LOWSHELF_FILTER
        initializeFilter();
#endif
        result = engine->synthesizeText(utfText, audiobuffer.content, audiobuffer.length, static_cast<void*>(&cb_handler));
        env->ReleaseStringUTFChars(text, utfText);
    }
    return result;
}

static jint
tts_abort(JNIEnv* env, jobject obj)
{
    TtsEngine* engine = getEngine(env, obj);
    jint result = TTS_FAILURE;
    if (engine)
        result = engine->stop();
    return result;
}


// Java class binding

static const char* className = "tts/synth/PicoTtsEngine";
static const JNINativeMethod methods[] =
  {
    { "initialize", "(Ljava/lang/String;)J", reinterpret_cast<void*>(initialize) },
    { "shutdown", "()V", reinterpret_cast<void*>(shutdown) },
    { "hasVoiceFor", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", reinterpret_cast<void*>(hasVoiceFor) },
    { "setLanguage", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I", reinterpret_cast<void*>(setLanguage) },
    { "getCurrentLanguage", "()[Ljava/lang/String;", reinterpret_cast<void*>(getCurrentLanguage) },
    { "setProperty", "(Ljava/lang/String;Ljava/lang/String;)I", reinterpret_cast<void*>(setProperty) },
    { "speak", "(Ljava/lang/String;)I", reinterpret_cast<void*>(speak) },
    { "abort", "()I", reinterpret_cast<void*>(tts_abort) }
  };

extern "C" {

JNIEXPORT jint
JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
    return bindJavaRepresentation(vm, className, methods, sizeof(methods) / sizeof(JNINativeMethod));
}

}
