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
}

description = "Hedera Services API Utilities"

configurations.all {
    exclude("javax.annotation", "javax.annotation-api")

    exclude("io.grpc", "grpc-core")
    exclude("io.grpc", "grpc-context")
    exclude("io.grpc", "grpc-api")
    exclude("io.grpc", "grpc-testing")
}

dependencies {
    api(project(":hedera-node:hedera-evm"))
    annotationProcessor(libs.dagger.compiler)

    implementation(libs.helidon.io.grpc)
    implementation(libs.bundles.di)
    implementation(libs.hapi)
    implementation(libs.bundles.logging)
    implementation(libs.protobuf.java)
    implementation(libs.jackson)
    implementation(libs.swirlds.common)
    implementation(libs.bundles.bouncycastle)
    implementation(libs.headlong)
    implementation(libs.besu.secp256k1)
    implementation(libs.commons.codec)
    implementation(libs.jna)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(testLibs.bundles.testing)
    itestImplementation(libs.hapi)
}
