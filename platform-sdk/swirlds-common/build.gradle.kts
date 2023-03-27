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
  id("com.swirlds.platform.conventions")
  id("com.swirlds.platform.library")
  id("com.swirlds.platform.maven-publish")
  id("org.gradle.java-test-fixtures")
}

dependencies {
  // Individual Dependencies
  api(project(":swirlds-logging"))
  api(project(":swirlds-config-api"))
  implementation(project(":swirlds-base"))
  implementation(libs.classgraph)
  implementation(libs.commons.codec)
  implementation(libs.prometheus.httpserver) {
    exclude("io.prometheus", "simpleclient_tracer_otel")
    exclude("io.prometheus", "simpleclient_tracer_otel_agent")
  }
  compileOnly(libs.spotbugs.annotations)

  // Bundle Dependencies
  api(libs.bundles.cryptography.core)
  runtimeOnly(libs.bundles.cryptography.runtime)
  implementation(libs.bundles.logging.impl)
  compileOnly(libs.spotbugs.annotations)

  // Test Dependencies
  testImplementation(testLibs.bundles.junit)
  testImplementation(testLibs.bundles.mocking)
  testImplementation(testLibs.bundles.utils)
  testImplementation(project(":swirlds-config-impl"))
  testImplementation(project(":swirlds-unit-tests:common:swirlds-test-framework"))
  testFixturesImplementation(project(":swirlds-base"))
}
