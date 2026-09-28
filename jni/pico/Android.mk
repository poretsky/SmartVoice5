# SVOX Pico TTS Engine

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

include $(LOCAL_PATH)/lib/Android.mk \
        $(LOCAL_PATH)/expat/Android.mk \
        $(LOCAL_PATH)/tts/Android.mk
