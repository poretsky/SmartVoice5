LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := expat

LOCAL_SRC_FILES := \
	lib/xmlparse.c \
	lib/xmlrole.c \
	lib/xmltok.c

LOCAL_CFLAGS := \
    -Wall \
    -Wmissing-prototypes -Wstrict-prototypes \
    -Wno-unused-parameter -Wno-missing-field-initializers \
    -fexceptions \
    -DHAVE_EXPAT_CONFIG_H

LOCAL_C_INCLUDES += \
	$(LOCAL_PATH)/lib

LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/lib
LOCAL_EXPORT_LDLIBS := -lc

include $(BUILD_STATIC_LIBRARY)
