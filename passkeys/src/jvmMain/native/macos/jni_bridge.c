// jni_bridge.c — JNI bridge for the macOS passkey backend.
// Wraps the Swift `@_cdecl` exports in ApplePasskeyBridge.swift and registers
// them as native methods on `ApplePasskeyNativeBridge` (Kotlin `external fun`s).
//
// Request and response JSON cross the boundary as UTF-8 byte arrays, not
// jstrings: GetStringUTFChars/NewStringUTF use JNI *modified* UTF-8, which does
// not round-trip supplementary-plane characters or embedded NULs through
// Swift's real UTF-8.

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

// Swift @_cdecl exports.
extern char* passkeyCreate(const char* requestJson, int64_t windowHandle);
extern char* passkeyAuthenticate(const char* requestJson, int64_t windowHandle);
extern void  passkeyFreeString(char* ptr);

static jbyteArray run(JNIEnv* env, jbyteArray requestJson, jlong windowHandle,
                      char* (*fn)(const char*, int64_t)) {
    if (!requestJson) return NULL;
    jsize len = (*env)->GetArrayLength(env, requestJson);
    char* cReq = (char*)malloc((size_t)len + 1);
    if (!cReq) return NULL;
    (*env)->GetByteArrayRegion(env, requestJson, 0, len, (jbyte*)cReq);
    cReq[len] = '\0';

    char* result = fn(cReq, (int64_t)windowHandle);
    free(cReq);
    if (!result) return NULL;

    size_t outLen = strlen(result);
    jbyteArray out = (*env)->NewByteArray(env, (jsize)outLen);
    if (out) (*env)->SetByteArrayRegion(env, out, 0, (jsize)outLen, (const jbyte*)result);
    passkeyFreeString(result);
    return out;
}

static jbyteArray JNICALL jni_create(JNIEnv* env, jclass cls, jbyteArray requestJson, jlong windowHandle) {
    (void)cls;
    return run(env, requestJson, windowHandle, passkeyCreate);
}

static jbyteArray JNICALL jni_authenticate(JNIEnv* env, jclass cls, jbyteArray requestJson, jlong windowHandle) {
    (void)cls;
    return run(env, requestJson, windowHandle, passkeyAuthenticate);
}

static const JNINativeMethod methods[] = {
    { "nCreate",       "([BJ)[B", (void*)jni_create },
    { "nAuthenticate", "([BJ)[B", (void*)jni_authenticate },
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
