// Russian TTS library java interface

#include <string>

#include <cstdlib>
#include <cstring>

#include <jni_bootstrap.hpp>

#include <ru_tts.h>
#include <lexdb.h>


// Local classes

// Callback interface
class cb_handler_t
{
public:
  cb_handler_t(JNIEnv* cb_env, jobject cb_obj, jclass cls):
    env(cb_env),
    obj(cb_obj)
  {
    method = env->GetMethodID(cls, "speechCallback", "([B)Z");
  }

  int executeCallback(void* buffer, size_t size)
  {
    jbyteArray data = env->NewByteArray(size);
    env->SetByteArrayRegion(data, 0, size, reinterpret_cast<jbyte*>(buffer));
    return static_cast<int>(env->CallBooleanMethod(obj, method, data));
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
      content = new char[size];
    else content = 0;
  }

  ~auto_buffer_t(void)
  {
    if (content)
      delete[] content;
  }

  size_t length;
  char* content;
};


// Local data

// Recoding table for koi8-r
static const char koi[] =
  {
    0xE1, 0xE2, 0xF7, 0xE7, 0xE4, 0xE5, 0xF6, 0xFA,
    0xE9, 0xEA, 0xEB, 0xEC, 0xED, 0xEE, 0xEF, 0xF0,
    0xF2, 0xF3, 0xF4, 0xF5, 0xE6, 0xE8, 0xE3, 0xFE,
    0xFB, 0xFD, 0xFF, 0xF9, 0xF8, 0xFC, 0xE0, 0xF1,
    0xC1, 0xC2, 0xD7, 0xC7, 0xC4, 0xC5, 0xD6, 0xDA,
    0xC9, 0xCA, 0xCB, 0xCC, 0xCD, 0xCE, 0xCF, 0xD0,
    0xD2, 0xD3, 0xD4, 0xD5, 0xC6, 0xC8, 0xC3, 0xDE,
    0xDB, 0xDD, 0xDF, 0xD9, 0xD8, 0xDC, 0xC0, 0xD1
  };

// Additional character codes
static const char blank = 0x20;
static const char koi_uc_yo = 0xB3;
static const char koi_lc_yo = 0xA3;
static const jchar utf_uc_yo = 0x0401;
static const jchar utf_lc_yo = 0x0451;

static const char* translit_x = "ks";
static const char* translit_w = "v";

// Code bases for Russian letters
static const int utf_codebase = 0x0410;
static const int extended_codebase = 0x80;


// Local functions

// Transfer control to the Java callback
int
callback(void* buffer, size_t size, void* user_data)
{
  return (buffer && size && user_data) ?
    reinterpret_cast<cb_handler_t*>(user_data)->executeCallback(buffer, size) :
    0;
}

bool
isKoiLetter(unsigned char c)
{
  return (c > 191) || (c == koi_lc_yo) || (c == koi_uc_yo);
}

bool
isAccent(unsigned char c)
{
  return (c == '+') || (c == '=');
}


// Exported native method

static void
speak(JNIEnv* env, jobject obj, jstring text)
{
  jclass cls = env->GetObjectClass(obj);
  cb_handler_t cb_handler(env, obj, cls);
  int textsize = static_cast<int>(env->GetStringLength(text));
  std::string koiText;
  auto_buffer_t audiobuffer(static_cast<size_t>(env->GetIntField(obj, env->GetFieldID(cls, "audioBufferSize", "I"))));

  const jchar* utftext = env->GetStringChars(text, 0);
  for (int i = 0; i < textsize; i++)
    switch (utftext[i])
      {
      case utf_uc_yo:
        koiText += koi_uc_yo;
        break;
      case utf_lc_yo:
        koiText += koi_lc_yo;
        break;
      case 'x':
      case 'X':
        koiText += translit_x;
        break;
      case 'w':
      case 'W':
        koiText += translit_w;
        break;
      default:
        if ((utftext[i] >= utf_codebase) && (utftext[i] < (utf_codebase + sizeof(koi))))
          koiText += koi[utftext[i] - utf_codebase];
        else if ((utftext[i] < extended_codebase) && (utftext[i] > blank))
          koiText += utftext[i];
        else koiText += blank;
      }
  env->ReleaseStringChars(text, utftext);

  if (textsize > 0)
    {
      const char *strToSpeak = koiText.c_str();
      std::string preparedText;
      if (env->GetBooleanField(obj, env->GetFieldID(cls, "useRulex", "Z")))
        {
          jstring pathString = reinterpret_cast<jstring>(env->GetObjectField(obj, env->GetFieldID(cls, "rulexPath", "Ljava/lang/String;")));
          const char* pathChars = env->GetStringUTFChars(pathString, 0);
          std::string rulexPath;
          if (pathChars)
            {
              rulexPath.append(pathChars, env->GetStringUTFLength(pathString));
              env->ReleaseStringUTFChars(pathString, pathChars);
            }
          RULEXDB *lexDB = rulexPath.empty() ? 0 : rulexdb_open(rulexPath.c_str());
          if (lexDB)
            {
              std::string lexKey;
              auto_buffer_t lexVal(RULEXDB_BUFSIZE);
              const char *s = koiText.c_str();
              while (*s)
                {
                  int n;
                  bool accented = false;
                  for (n = 0; s[n] && !isKoiLetter(s[n]); n++);
                  if (n)
                    {
                      preparedText.append(s, n);
                      s += n;
                    }
                  lexKey.erase();
                  for (n = 0; n <= RULEXDB_MAX_KEY_SIZE; n++)
                    if (isKoiLetter(s[n]))
                      {
                        if (static_cast<unsigned char>(s[n]) > 223)
                          lexKey += s[n] - 32;
                        else if (s[n] == koi_uc_yo)
                          lexKey += koi_lc_yo;
                        else lexKey += s[n];
                      }
                    else if (isAccent(s[n]))
                      {
                        lexKey += s[n];
                        accented = true;
                      }
                    else break;
                  if ((n > 0) && (n <= RULEXDB_MAX_KEY_SIZE) && !accented)
                    {
                      rulexdb_search(lexDB, lexKey.c_str(), lexVal.content, 0);
                      preparedText += lexVal.content;
                    }
                  else preparedText.append(s, n);
                  s += n;
                }
              rulexdb_close(lexDB);
              strToSpeak = preparedText.c_str();
            }
        }

      ru_tts_conf_t config;
      config.speech_rate = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "speechRate", "I")));
      config.voice_pitch = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "pitch", "I")));
      config.intonation = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "intonation", "I")));
      config.general_gap_factor = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "generalGapFactor", "I")));
      config.comma_gap_factor = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "commaGapFactor", "I")));
      config.dot_gap_factor = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "dotGapFactor", "I")));
      config.semicolon_gap_factor = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "semicolonGapFactor", "I")));
      config.colon_gap_factor = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "colonGapFactor", "I")));
      config.question_gap_factor = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "questionGapFactor", "I")));
      config.exclamation_gap_factor = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "exclamationGapFactor", "I")));
      config.intonational_gap_factor = static_cast<int>(env->GetIntField(obj, env->GetFieldID(cls, "intonationalGapFactor", "I")));
      config.flags = 0;
      if (static_cast<bool>(env->GetBooleanField(obj, env->GetFieldID(cls, "decimalPoint", "Z"))))
        config.flags |= DEC_SEP_POINT;
      if (static_cast<bool>(env->GetBooleanField(obj, env->GetFieldID(cls, "decimalComma", "Z"))))
        config.flags |= DEC_SEP_COMMA;
      if (static_cast<bool>(env->GetBooleanField(obj, env->GetFieldID(cls, "altVoice", "Z"))))
        config.flags |= USE_ALTERNATIVE_VOICE;
      ru_tts_transfer(&config, strToSpeak, audiobuffer.content, audiobuffer.length, callback, &cb_handler);
    }
}


// Java class binding

static const char* className = "tts/synth/RussianVoiceEngine";
static const JNINativeMethod method = { "speak", "(Ljava/lang/String;)V", reinterpret_cast<void*>(speak) };

extern "C" {

JNIEXPORT jint
JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
  return bindJavaRepresentation(vm, className, &method, 1);
}

}
