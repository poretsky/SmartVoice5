LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := ruvoicesynth

LOCAL_C_INCLUDES := $(LOCAL_PATH)/ru_tts/src

LOCAL_SRC_FILES := ru_tts_jni.cpp \
	ru_tts/src/text2speech.c \
	ru_tts/src/synth.c \
	ru_tts/src/utterance.c \
	ru_tts/src/speechrate_control.c \
	ru_tts/src/time_planner.c \
	ru_tts/src/numerics.c \
	ru_tts/src/intonator.c \
	ru_tts/src/transcription.c \
	ru_tts/src/male.c \
	ru_tts/src/female.c \
	ru_tts/src/sink.c \
	ru_tts/src/soundproducer.c

LOCAL_STATIC_LIBRARIES := rulex bootstrap

LOCAL_LDFLAGS += -Wl,--version-script=$(LOCAL_PATH)/../smartvoice.vscript

include $(BUILD_SHARED_LIBRARY)


include $(LOCAL_PATH)/rulex/Android.mk \
        $(LOCAL_PATH)/bdb/Android.mk
