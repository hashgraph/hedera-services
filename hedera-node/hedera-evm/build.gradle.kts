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

  compileOnlyApi(libs.spotbugs.annotations)
  api(libs.slf4j.api)
  api(libs.besu.evm)
  api(libs.besu.secp256k1)
  api(libs.swirlds.common)
  api(libs.besu.datatypes)
  api(project(":hedera-node:hapi"))
  api(libs.guava) // TODO: we should remove the internal usage of guava

  implementation(libs.jna)
  implementation(libs.caffeine)
  implementation(libs.headlong)
  implementation(libs.dagger.compiler)
  implementation(libs.javax.inject)

  testImplementation(testLibs.mockito.jupiter)
  testImplementation(testLibs.mockito.inline)
  testImplementation(testLibs.junit.jupiter.api)
}
