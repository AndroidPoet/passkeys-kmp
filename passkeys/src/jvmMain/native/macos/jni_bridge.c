// jni_bridge.c — JNI bridge for the macOS passkey backend.
// Wraps the Swift `@_cdecl` exports in ApplePasskeyBridge.swift and registers
// them as native methods on `ApplePasskeyNativeBridge` (Kotlin `external fun`s).

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

// Swift @_cdecl exports.
extern char* passkeyCreate(const char* requestJson, int64_t windowHandle);
extern char* passkeyAuthenticate(const char* requestJson, int64_t windowHandle);
extern void  passkeyFreeString(char* ptr);

static jstring run(JNIEnv* env, jstring requestJson, jlong windowHandle,
                   char* (*fn)(const char*, int64_t)) {
    if (!requestJson) return NULL;
    const char* cReq = (*env)->GetStringUTFChars(env, requestJson, NULL);
    if (!cReq) return NULL;

    char* result = fn(cReq, (int64_t)windowHandle);
    (*env)->ReleaseStringUTFChars(env, requestJson, cReq);

    jstring out = result ? (*env)->NewStringUTF(env, result) : NULL;
    if (result) passkeyFreeString(result);
    return out;
}

static jstring JNICALL jni_create(JNIEnv* env, jclass cls, jstring requestJson, jlong windowHandle) {
    (void)cls;
    return run(env, requestJson, windowHandle, passkeyCreate);
}

static jstring JNICALL jni_authenticate(JNIEnv* env, jclass cls, jstring requestJson, jlong windowHandle) {
    (void)cls;
    return run(env, requestJson, windowHandle, passkeyAuthenticate);
}

static const JNINativeMethod methods[] = {
    { "nCreate",       "(Ljava/lang/String;J)Ljava/lang/String;", (void*)jni_create },
    { "nAuthenticate", "(Ljava/lang/String;J)Ljava/lang/String;", (void*)jni_authenticate },
};

#define BRIDGE_CLASS "io/github/androidpoet/passkeys/internal/ApplePasskeyNativeBridge"

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)reserved;
    JNIEnv* env = NULL;
    if ((*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass cls = (*env)->FindClass(env, BRIDGE_CLASS);
    if (!cls) return JNI_ERR;
    if ((*env)->RegisterNatives(env, cls, methods,
                                sizeof(methods) / sizeof(methods[0])) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
