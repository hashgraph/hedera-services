
#include <jni.h>
#include "com_hedera_common_nativesupport_jni_Greeter.h"  // This is the generated header file

// Implementation of the native method
extern "C" JNIEXPORT jstring JNICALL  Java_com_hedera_common_nativesupport_jni_Greeter_getGreeting(JNIEnv *env, jobject) {
   char *helloStr = "Hello, World from C++!";
   return env->NewStringUTF(helloStr);
}