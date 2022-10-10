/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
plugins {
    id("com.hedera.hashgraph.conventions")
    id("de.jjohannes.extra-java-module-info") version "0.15"
}

description = "Hedera Application"

dependencies {
    implementation(project(":modules:hedera-app-api"))
    implementation(libs.jsr305.annotation)
    implementation(libs.grpc.protobuf)
    implementation(libs.swirlds.common)
    implementation(libs.swirlds.merkle)
    testImplementation(libs.bundles.bouncycastle)
}

/** Patch libraries that are not Java 9 modules */
extraJavaModuleInfo {
    automaticModule("org.eclipse.microprofile.health:microprofile-health-api", "microprofile.health.api")

    automaticModule("com.google.j2objc:j2objc-annotations", "j2objc.annotations")
    automaticModule("io.perfmark:perfmark-api", "perfmark.api")

    automaticModule("com.google.guava:failureaccess", "failureaccess")
    automaticModule("com.google.guava:listenablefuture", "listenablefuture")

    automaticModule("io.grpc:grpc-api", "io.grpc") {
        mergeJar("io.grpc:grpc-context")
        mergeJar("io.grpc:grpc-core")
    }
    automaticModule("io.grpc:grpc-netty", "grpc.netty")
    automaticModule("io.grpc:grpc-services", "grpc.services")
    automaticModule("io.grpc:grpc-protobuf", "grpc.protobuf")
    automaticModule("io.grpc:grpc-stub", "grpc.stub")
    automaticModule("io.grpc:grpc-protobuf-lite", "grpc.protobuf.lite")

    automaticModule("com.google.code.findbugs:jsr305", "jsr305")
    automaticModule("com.offbynull.portmapper:portmapper", "portmapper")
    automaticModule("com.goterl:lazysodium-java", "lazysodium.java")
    automaticModule("org.openjfx:javafx-base", "javafx.base")

    failOnMissingModuleInfo.set(false)
}

