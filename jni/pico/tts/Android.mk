LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := svttspico

LOCAL_SRC_FILES := \
	PicoTtsEngine-jni.cpp \
	com_svox_picottsengine.cpp \
	svox_ssml_parser.cpp \
	cutils/strdup16to8.c \
	cutils/strdup8to16.c

#LOCAL_CFLAGS += -DUSE_LOWSHELF_FILTER
LOCAL_STATIC_LIBRARIES := svoxpico expat bootstrap
LOCAL_LDLIBS += -llog
LOCAL_LDFLAGS += -Wl,--version-script=$(LOCAL_PATH)/../../smartvoice.vscript

include $(BUILD_SHARED_LIBRARY)
