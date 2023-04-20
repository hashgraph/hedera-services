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

plugins { id("com.hedera.hashgraph.conventions") }

description = "Default Hedera Token Service Implementation"

configurations.all {
  exclude("javax.annotation", "javax.annotation-api")

  exclude("io.grpc", "grpc-core")
  exclude("io.grpc", "grpc-context")
  exclude("io.grpc", "grpc-api")
  exclude("io.grpc", "grpc-testing")
}

dependencies {
  implementation(project(":hedera-node:hapi"))
  testImplementation(project(mapOf("path" to ":hedera-node:hedera-app")))
  annotationProcessor(libs.dagger.compiler)
  api(project(":hedera-node:hedera-token-service"))
  implementation(project(":hedera-node:hedera-mono-service"))
  implementation(libs.bundles.di)
  implementation(libs.pbj.runtime)

  implementation(libs.swirlds.virtualmap)
  implementation(libs.swirlds.jasperdb)
  testImplementation(testLibs.bundles.testing)
  testImplementation(testFixtures(project(":hedera-node:hedera-mono-service")))
  testImplementation(testFixtures(project(":hedera-node:hedera-app-spi")))
  testImplementation(testLibs.mockito.inline)
  testImplementation(project(":hedera-node:hedera-app-spi"))
}
