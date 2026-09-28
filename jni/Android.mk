LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

include $(LOCAL_PATH)/bootstrap/Android.mk \
        $(LOCAL_PATH)/pico/Android.mk \
        $(LOCAL_PATH)/ruvoicesynth/Android.mk
