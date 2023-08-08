/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
    id("com.google.protobuf") version "0.9.4"
}

application.mainClass.set("com.swirlds.demo.platform.PlatformTestingToolMain")

dependencies {
    javaModuleDependencies {
        testImplementation(gav("com.swirlds.test.framework"))
        testImplementation(gav("org.apache.logging.log4j.core"))
        testImplementation(gav("org.bouncycastle.provider"))
        testImplementation(gav("org.junit.jupiter.params"))
        testImplementation(gav("org.junit.jupiter.api"))
        testImplementation(gav("org.mockito"))
    }
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

tasks.withType<Javadoc>().configureEach { enabled = false }

// TODO possibly hitting bug with included build
// https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/pull/916
// Remove after next dependency analysis plugin update
dependencyAnalysis.issues { all { onUnusedDependencies { exclude("com.swirlds:swirlds-common") } } }
