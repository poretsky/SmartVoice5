// jni_bootstrap.hpp -- Binding java representation

#ifndef JNI_BOOTSTRAP_HPP
#define JNI_BOOTSTRAP_HPP

#include <cstddef>

#include <jni.h>

extern jint bindJavaRepresentation(JavaVM* vm, const char* className, const JNINativeMethod* methods, size_t nMethods);

#endif
