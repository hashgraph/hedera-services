/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
  id("com.hedera.hashgraph.benchmark-conventions")
  `java-test-fixtures`
}

description = "Hedera Application - MONO Service Implementation"

configurations.all {
  exclude("javax.annotation", "javax.annotation-api")
  exclude("com.google.code.findbugs", "jsr305")
  exclude("org.jetbrains", "annotations")
  exclude("org.checkerframework", "checker-qual")

  exclude("io.grpc", "grpc-core")
  exclude("io.grpc", "grpc-context")
  exclude("io.grpc", "grpc-api")
  exclude("io.grpc", "grpc-testing")

  exclude("org.hamcrest", "hamcrest-core")
}

dependencies {
  annotationProcessor(libs.dagger.compiler)

  api(project(":hedera-node:hedera-evm"))
  api(project(":hedera-node:hedera-app-spi"))
  api(project(":hedera-node:hedera-admin-service"))
  api(project(":hedera-node:hedera-consensus-service"))
  api(project(":hedera-node:hedera-file-service"))
  api(project(":hedera-node:hedera-network-service"))
  api(project(":hedera-node:hedera-schedule-service"))
  api(project(":hedera-node:hedera-smart-contract-service"))
  api(project(":hedera-node:hedera-token-service"))
  api(project(":hedera-node:hedera-util-service"))

  implementation(project(":hedera-node:hapi-fees"))
  implementation(project(":hedera-node:hapi-utils"))

  implementation(libs.bundles.besu) {
    exclude(group = "org.hyperledger.besu", module = "secp256r1")
  }
  implementation(libs.bundles.di)
  implementation(libs.grpc.stub)
  implementation(libs.bundles.logging)
  implementation(libs.bundles.swirlds)
  implementation(libs.caffeine)
  implementation(libs.helidon.io.grpc)
  implementation(libs.headlong)
  implementation(variantOf(libs.netty.transport.native.epoll) { classifier("linux-x86_64") })
  implementation(libs.commons.codec)
  implementation(libs.commons.io)
  implementation(libs.commons.collections4)
  implementation(libs.eclipse.collections)

  testImplementation(testLibs.bundles.testing)
  testImplementation(testLibs.classgraph)
  testCompileOnly(libs.spotbugs.annotations)

  testFixturesApi(project(":hedera-node:hedera-app-spi"))
  testFixturesApi(project(":hedera-node:hapi-utils"))
  testFixturesApi(libs.swirlds.merkle)
  testFixturesApi(libs.swirlds.virtualmap)
  testFixturesApi(libs.hapi)
  testFixturesApi(libs.commons.codec)
  testFixturesImplementation(testLibs.bundles.testing)

  jmhImplementation(libs.swirlds.common)

  runtimeOnly(libs.bundles.netty)
}

val apt = configurations.create("apt")

dependencies {
  implementation(project(":hedera-node:hedera-app-spi"))
  testImplementation("org.jetbrains:annotations:20.1.0")
  @Suppress("UnstableApiUsage") apt(libs.dagger.compiler)
}

tasks.withType<JavaCompile> { options.annotationProcessorPath = apt }

val jmhDaggerSources = file("build/generated/sources/annotationProcessor/java/jmh")

java.sourceSets["jmh"].java.srcDir(jmhDaggerSources)

// Replace variables in semantic-version.properties with build variables
tasks.processResources {
  filesMatching("semantic-version.properties") {
    filter { line: String ->
      if (line.contains("hapi-proto.version")) {
        "hapi.proto.version=" + libs.versions.hapi.version.get()
      } else if (line.contains("project.version")) {
        "hedera.services.version=" + project.version
      } else {
        line
      }
    }
  }
}
