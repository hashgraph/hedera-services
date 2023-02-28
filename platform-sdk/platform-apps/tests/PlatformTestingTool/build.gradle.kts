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
  id("com.swirlds.platform.application")
  id("com.swirlds.platform.protobuf")
}

dependencies {
  // Individual Dependencies
  implementation(project(":swirlds-merkle"))
  implementation(libs.commons.math3)
  implementation(libs.commons.io)
  implementation(libs.protobuf)

  // Bundle Dependencies
  implementation(libs.bundles.logging.impl)

  // Test Dependencies

  // These should not be implementation() based deps, but this requires refactoring to eliminate.
  implementation(project(":swirlds-unit-tests:common:swirlds-test-framework"))
  implementation(project(":swirlds-unit-tests:common:swirlds-common-test"))
  implementation(project(":swirlds-unit-tests:structures:swirlds-merkle-test"))

  testImplementation(testLibs.bundles.junit)
  testImplementation(testLibs.bundles.mocking)
}

tasks.withType<Javadoc>() { enabled = false }
