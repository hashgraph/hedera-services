
#include <jni.h>
#include "com_hedera_nativesupport_jni_Greeter.h"  // This is the generated header file

// Implementation of the native method
JNIEXPORT jstring JNICALL  Java_com_hedera_nativesupport_jni_Greeter_getGreeting(JNIEnv *env, jobject) {
   const char *helloStr = "Hello, World from C++!";
   return env->NewStringUTF(helloStr);
}