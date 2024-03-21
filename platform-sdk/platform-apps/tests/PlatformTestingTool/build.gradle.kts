/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.protobuf.gradle.ProtobufExtract

plugins {
    id("com.hedera.hashgraph.application")
    id("com.google.protobuf")
}

// Remove the following line to enable all 'javac' lint checks that we have turned on by default
// and then fix the reported issues.
tasks.compileJava { options.compilerArgs.add("-Xlint:-exports") }

application.mainClass.set("com.swirlds.demo.platform.PlatformTestingToolMain")

testModuleInfo {
    requires("org.apache.logging.log4j.core")
    requires("org.bouncycastle.provider")
    requires("org.junit.jupiter.params")
    requires("org.junit.jupiter.api")
    requires("org.mockito")
}

protobuf { protoc { artifact = "com.google.protobuf:protoc:3.21.5" } }

configurations {
    // Give proto compile access to the dependency versions
    compileProtoPath { extendsFrom(configurations.internal.get()) }
    testCompileProtoPath { extendsFrom(configurations.internal.get()) }
}

tasks.withType<ProtobufExtract>().configureEach {
    if (name == "extractIncludeProto") {
        enabled = false
    }
}
