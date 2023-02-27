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
  `java-test-fixtures`
}

description = "Hedera Application - SPI"

configurations.all {
  exclude("javax.annotation", "javax.annotation-api")
  exclude("com.google.code.findbugs", "jsr305")
  exclude("org.jetbrains", "annotations")
  exclude("org.checkerframework", "checker-qual")

  exclude("io.grpc", "grpc-core")
  exclude("io.grpc", "grpc-context")
  exclude("io.grpc", "grpc-api")
  exclude("io.grpc", "grpc-testing")
}

dependencies {
  api(libs.pbj.runtime)
  api(libs.hapi)
  api(libs.jsr305.annotation)
  api(project(":hedera-node:hapi"))
  implementation(libs.swirlds.common)
  compileOnlyApi(libs.spotbugs.annotations)

  testImplementation(testLibs.bundles.testing)
  testCompileOnly(libs.spotbugs.annotations)

  testFixturesCompileOnly(libs.spotbugs.annotations)
  testFixturesCompileOnly(testLibs.assertj.core)
}
