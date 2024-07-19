
#include <jni.h>
#include <iostream>
#include "com_swirlds_platform_base_example_jni_HelloWorld.h"  // This is the generated header file

// Implementation of the native method
JNIEXPORT void JNICALL Java_com_swirlds_platform_base_example_jni_HelloWorld_printHelloWorld(JNIEnv *, jobject) {
    std::cout << "Hello, World from C++!" << std::endl;
}