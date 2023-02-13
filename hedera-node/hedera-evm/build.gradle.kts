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
plugins {
    id("com.hedera.hashgraph.conventions")
    id("com.hedera.hashgraph.maven-publish")
}

group = "com.hedera.evm"
description = "Hedera EVM - API"

configurations.all {
    exclude("javax.annotation", "javax.annotation-api")
    exclude("com.google.code.findbugs", "jsr305")
    exclude("org.jetbrains", "annotations")
    exclude("org.checkerframework", "checker-qual")
    exclude("com.google.errorprone", "error_prone_annotations")
    exclude("com.google.j2objc", "j2objc-annotations")

    exclude("io.grpc", "grpc-core")
    exclude("io.grpc", "grpc-context")
    exclude("io.grpc", "grpc-api")
    exclude("io.grpc", "grpc-testing")
    exclude("io.grpc", "grpc-stub")
}

dependencies {
    annotationProcessor(libs.dagger.compiler)

    api(libs.swirlds.common) {
        exclude(group = "org.apache.commons", module = "commons-lang3")
        exclude(group = "com.fasterxml.jackson.core", module = "jackson-databind")
        exclude(group = "com.fasterxml.jackson.datatype", module = "jackson-datatype-jsr310")
        exclude(group = "com.fasterxml.jackson.dataformat", module = "jackson-dataformat-yaml")
        exclude(group = "com.goterl", module = "lazysodium-java")
        exclude(group = "org.bouncycastle", module = "bcpkix-jdk15on")
        exclude(group = "com.swirlds", module = "swirlds-cli")
        exclude(group = "com.swirlds", module = "swirlds-logging")
    }
    api(libs.besu.evm) {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    api(libs.slf4j.api)
    api(libs.besu.secp256k1)
    api(libs.besu.datatypes) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
        exclude(group = "org.connid", module = "framework")
        exclude(group = "org.connid", module = "framework-internal")
    }
    compileOnlyApi(libs.spotbugs.annotations)
    api(libs.hapi) {
        // this is an android version, not a jre version
        exclude("com.google.guava", "guava")
        exclude(group = "io.grpc", module = "grpc-netty")
        exclude(group = "io.netty", module = "netty-codec-http2")
        exclude(group = "io.netty", module = "netty-handler-proxy")
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "io.grpc", module = "grpc-stub")
        exclude(group = "net.i2p.crypto", module = "eddsa")
        exclude(group = "com.google.api.grpc", module = "proto-google-common-protos")
        exclude(group = "io.grpc", module = "grpc-protobuf-lite")
    }

    implementation(group = "com.google.dagger", name = "dagger", version = "2.42") {
        exclude(group = "javax.inject", module = "javax.inject")
    }
    implementation(libs.jna)
    implementation(libs.caffeine)
    implementation(libs.headlong)
    implementation(libs.javax.inject)
    implementation(libs.guava) {
        exclude(group = "com.google.guava", module = "failureaccess")
        exclude(group = "com.google.guava", module = "listenablefuture")
    }


    testImplementation(testLibs.mockito.jupiter)
    testImplementation(testLibs.mockito.inline)
    testImplementation(testLibs.junit.jupiter.api)
}
