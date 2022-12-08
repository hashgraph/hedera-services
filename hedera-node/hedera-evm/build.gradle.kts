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
    id("com.hedera.hashgraph.maven-publish")
}

group = "com.hedera.evm"
description = "Hedera EVM - API"

configurations.all {
    exclude("javax.annotation", "javax.annotation-api")
    exclude("com.google.code.findbugs", "jsr305")

    exclude("io.grpc", "grpc-core")
    exclude("io.grpc", "grpc-context")
    exclude("io.grpc", "grpc-api")
    exclude("io.grpc", "grpc-testing")
    exclude("io.grpc", "grpc-stub")
}

dependencies {

    api(libs.besu.evm)
    api(libs.besu.datatypes)
    api(libs.swirlds.common)
    implementation(libs.helidon.io.grpc)
    api(libs.besu.secp256k1)
    implementation(libs.jna)
    implementation(libs.caffeine)
    implementation(libs.guava)
    implementation(libs.hapi) {
        exclude("com.google.guava", "guava") // this is an android version, not a jre version
    }
    implementation(libs.javax.inject)
    implementation(libs.headlong)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(testLibs.mockito.jupiter)
}
