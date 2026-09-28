// jni_bootstrap.cpp -- Binding java representation

#include <cstddef>

#include <jni.h>

jint bindJavaRepresentation(JavaVM* vm, const char* className, const JNINativeMethod* methods, size_t nMethods)
{
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;
    jclass c = env->FindClass(className);
    if (c == nullptr)
        return JNI_ERR;
    int rc = env->RegisterNatives(c, methods, nMethods);
    if (rc != JNI_OK)
        return rc;
    return JNI_VERSION_1_6;
}
