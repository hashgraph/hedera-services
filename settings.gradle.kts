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

import me.champeau.gradle.igp.gitRepositories

// Add local maven build directory to plugin repos
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
  }
}

plugins {
  id("com.gradle.enterprise").version("3.11.4")
  // Use GIT plugin to clone HAPI protobuf files
  // See documentation https://melix.github.io/includegit-gradle-plugin/latest/index.html
  id("me.champeau.includegit").version("0.1.6")
}

include(":hedera-node")

include(":hedera-node:hedera-admin-service")

include(":hedera-node:hedera-admin-service-impl")

include(":hedera-node:hedera-consensus-service")

include(":hedera-node:hedera-consensus-service-impl")

include(":hedera-node:hedera-file-service")

include(":hedera-node:hedera-file-service-impl")

include(":hedera-node:hedera-network-service")

include(":hedera-node:hedera-network-service-impl")

include(":hedera-node:hedera-schedule-service")

include(":hedera-node:hedera-schedule-service-impl")

include(":hedera-node:hedera-smart-contract-service")

include(":hedera-node:hedera-smart-contract-service-impl")

include(":hedera-node:hedera-token-service")

include(":hedera-node:hedera-token-service-impl")

include(":hedera-node:hedera-util-service")

include(":hedera-node:hedera-util-service-impl")

include(":hedera-node:hapi-utils")

include(":hedera-node:hapi-fees")

include(":hedera-node:hapi")

include(":hedera-node:hedera-app")

include(":hedera-node:hedera-app-spi")

include(":hedera-node:hedera-evm")

include(":hedera-node:hedera-evm-impl")

include(":hedera-node:hedera-mono-service")

include(":hedera-node:test-clients")

// Enable Gradle Build Scan
gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
  }
}

gitRepositories {
  checkoutsDirectory.set(file(rootProject.projectDir.absolutePath + "/hedera-node/hapi/"))
  include("hedera-protobufs") {
    uri.set("https://github.com/hashgraph/hedera-protobufs.git")
    // choose tag or branch of HAPI you would like to test with
    // this looks for a tag in hedera-protobufs repo
    // This version needs to match tha HAPI version below in versionCatalogs
    tag.set("add-pbj-types-for-state")
    // do not load project from repo
    autoInclude.set(false)
  }
}

// Define the library catalogs available for projects to make use of
dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  versionCatalogs {
    // The libs of this catalog are the **ONLY** ones that are authorized to be part of the runtime
    // distribution. These libs can be depended on during compilation, or bundled as part of
    // runtime.
    create("libs") {
      // The HAPI API version to use, this need to match the tag set on gitRepositories above
      version("hapi-version", "0.37.0-allowance-SNAPSHOT")

      // Definition of version numbers for all libraries
      version("pbj-version", "0.5.1")
      version("besu-version", "22.10.1")
      version("besu-native-version", "0.6.1")
      version("bouncycastle-version", "1.70")
      version("caffeine-version", "3.0.6")
      version("eclipse-collections-version", "10.4.0")
      version("commons-codec-version", "1.15")
      version("commons-io-version", "2.11.0")
      version("commons-collections4-version", "4.4")
      version("commons-lang3-version", "3.12.0")
      version("dagger-version", "2.42")
      version("eddsa-version", "0.3.0")
      version("grpc-version", "1.50.2")
      version("guava-version", "31.1-jre")
      version("headlong-version", "6.1.1")
      version("helidon-version", "3.0.2")
      version("jackson-version", "2.13.3")
      version("javax-annotation-version", "1.3.2")
      version("javax-inject-version", "1")
      version("jetbrains-annotation-version", "16.0.2")
      version("log4j-version", "2.17.2")
      version("netty-version", "4.1.66.Final")
      version("protobuf-java-version", "3.19.4")
      version("slf4j-version", "2.0.3")
      version("swirlds-version", "0.37.0-adhoc.xc76224af")
      version("tuweni-version", "2.2.0")
      version("jna-version", "5.12.1")
      version("jsr305-version", "3.0.2")
      version("spotbugs-version", "4.7.3")
      version("helidon-grpc-version", "3.0.2")

      plugin("pbj", "com.hedera.pbj.pbj-compiler").versionRef("pbj-version")

      // List of bundles provided for us. When applicable, favor using these over individual
      // libraries.
      // Use when you need to use Besu
      bundle(
          "besu",
          listOf("besu-bls12-381", "besu-evm", "besu-datatypes", "besu-secp256k1", "tuweni-units"))
      // Use when you need to use bouncy castle
      bundle("bouncycastle", listOf("bouncycastle-bcprov-jdk15on", "bouncycastle-bcpkix-jdk15on"))
      // Use when you need to make use of dependency injection.
      bundle("di", listOf("javax-inject", "dagger-api"))
      // Use when you need a grpc server
      bundle("helidon", listOf("helidon-server", "helidon-grpc-server", "helidon-io-grpc"))
      // Use when you need logging
      bundle("logging", listOf("log4j-api", "log4j-core", "log4j-slf4j", "slf4j-api"))
      // Use when you need to depend upon netty
      bundle("netty", listOf("netty-handler", "netty-transport-native-epoll"))
      // Use when you depend upon all or swirlds
      bundle(
          "swirlds",
          listOf(
              "swirlds-common",
              "swirlds-platform-core",
              "swirlds-fchashmap",
              "swirlds-merkle",
              "swirlds-fcqueue",
              "swirlds-jasperdb",
              "swirlds-virtualmap",
              "swirlds-test-framework"))

      // Define the individual libraries
      library("pbj-runtime", "com.hedera.pbj", "pbj-runtime").versionRef("pbj-version")
      library("besu-bls12-381", "org.hyperledger.besu", "bls12-381")
          .versionRef("besu-native-version")
      library("besu-secp256k1", "org.hyperledger.besu", "secp256k1")
          .versionRef("besu-native-version")
      library("besu-evm", "org.hyperledger.besu", "evm").versionRef("besu-version")
      library("besu-datatypes", "org.hyperledger.besu", "besu-datatypes").versionRef("besu-version")
      library("bouncycastle-bcprov-jdk15on", "org.bouncycastle", "bcprov-jdk15on")
          .versionRef("bouncycastle-version")
      library("bouncycastle-bcpkix-jdk15on", "org.bouncycastle", "bcpkix-jdk15on")
          .versionRef("bouncycastle-version")
      library("caffeine", "com.github.ben-manes.caffeine", "caffeine")
          .versionRef("caffeine-version")
      library("eclipse-collections", "org.eclipse.collections", "eclipse-collections")
          .versionRef("eclipse-collections-version")
      library("commons-collections4", "org.apache.commons", "commons-collections4")
          .versionRef("commons-collections4-version")
      library("commons-codec", "commons-codec", "commons-codec").versionRef("commons-codec-version")
      library("commons-io", "commons-io", "commons-io").versionRef("commons-io-version")
      library("commons-lang3", "org.apache.commons", "commons-lang3")
          .versionRef("commons-lang3-version")
      library("dagger-api", "com.google.dagger", "dagger").versionRef("dagger-version")
      library("dagger-compiler", "com.google.dagger", "dagger-compiler")
          .versionRef("dagger-version")
      library("eddsa", "net.i2p.crypto", "eddsa").versionRef("eddsa-version")
      library("grpc-stub", "io.grpc", "grpc-stub").versionRef("grpc-version")
      library("grpc-protobuf", "io.grpc", "grpc-protobuf").versionRef("grpc-version")
      library("grpc-netty", "io.grpc", "grpc-netty").versionRef("grpc-version")
      library("guava", "com.google.guava", "guava").versionRef("guava-version")
      library("hapi", "com.hedera.hashgraph", "hedera-protobuf-java-api").versionRef("hapi-version")
      library("headlong", "com.esaulpaugh", "headlong").versionRef("headlong-version")
      library("helidon-server", "io.helidon.webserver", "helidon-webserver-http2")
          .versionRef("helidon-version")
      library("helidon-grpc-server", "io.helidon.grpc", "helidon-grpc-server")
          .versionRef("helidon-version")
      library("helidon-io-grpc", "io.helidon.grpc", "io.grpc").versionRef("helidon-version")
      library("jackson", "com.fasterxml.jackson.core", "jackson-databind")
          .versionRef("jackson-version")
      library("javax-annotation", "javax.annotation", "javax.annotation-api")
          .versionRef("javax-annotation-version")
      library("javax-inject", "javax.inject", "javax.inject").versionRef("javax-inject-version")
      library("jetbrains-annotation", "org.jetbrains", "annotations")
          .versionRef("jetbrains-annotation-version")
      library("jsr305-annotation", "com.google.code.findbugs", "jsr305")
          .versionRef("jsr305-version")
      library("log4j-api", "org.apache.logging.log4j", "log4j-api").versionRef("log4j-version")
      library("log4j-core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j-version")
      library("log4j-slf4j", "org.apache.logging.log4j", "log4j-slf4j-impl")
          .versionRef("log4j-version")
      library("netty-transport-native-epoll", "io.netty", "netty-transport-native-epoll")
          .versionRef("netty-version")
      library("netty-handler", "io.netty", "netty-handler").versionRef("netty-version")
      library("protobuf-java", "com.google.protobuf", "protobuf-java")
          .versionRef("protobuf-java-version")
      library("swirlds-common", "com.swirlds", "swirlds-common").versionRef("swirlds-version")
      library("slf4j-api", "org.slf4j", "slf4j-api").versionRef("slf4j-version")
      library("slf4j-simple", "org.slf4j", "slf4j-api").versionRef("slf4j-version")
      library("swirlds-platform-core", "com.swirlds", "swirlds-platform-core")
          .versionRef("swirlds-version")
      library("swirlds-fchashmap", "com.swirlds", "swirlds-fchashmap").versionRef("swirlds-version")
      library("swirlds-merkle", "com.swirlds", "swirlds-merkle").versionRef("swirlds-version")
      library("swirlds-fcqueue", "com.swirlds", "swirlds-fcqueue").versionRef("swirlds-version")
      library("swirlds-jasperdb", "com.swirlds", "swirlds-jasperdb").versionRef("swirlds-version")
      library("swirlds-virtualmap", "com.swirlds", "swirlds-virtualmap")
          .versionRef("swirlds-version")
      library("swirlds-test-framework", "com.swirlds", "swirlds-test-framework")
          .versionRef("swirlds-version")
      library("tuweni-units", "org.apache.tuweni", "tuweni-units").versionRef("tuweni-version")
      library("jna", "net.java.dev.jna", "jna").versionRef("jna-version")
      library("spotbugs-annotations", "com.github.spotbugs", "spotbugs-annotations")
          .versionRef("spotbugs-version")
    }

    // The libs of this catalog can be used for test or build uses.
    create("testLibs") {
      version("awaitility-version", "4.2.0")
      version("besu-internal-version", "22.1.1")
      version("commons-collections4-version", "4.4")
      version("hamcrest-version", "2.2")
      version("json-version", "20210307")
      version("junit5-version", "5.9.0")
      version("junit-pioneer-version", "2.0.1")
      version("helidon-version", "3.0.2")
      version("mockito-version", "4.6.1")
      version("picocli-version", "4.6.3")
      version("snakeyaml-version", "1.26")
      version("testcontainers-version", "1.17.2")
      version("classgraph-version", "4.8.65")
      version("assertj-version", "3.23.1")

      bundle("junit5", listOf("junit-jupiter-api", "junit-jupiter-params", "junit-jupiter"))
      bundle("mockito", listOf("mockito-inline", "mockito-jupiter"))
      bundle("testcontainers", listOf("testcontainers-core", "testcontainers-junit"))

      bundle(
          "testing",
          listOf(
              "junit-jupiter",
              "junit-jupiter-api",
              "junit-jupiter-params",
              "junit-pioneer",
              "mockito-inline",
              "mockito-jupiter",
              "hamcrest",
              "awaitility",
              "assertj-core"))

      library("awaitility", "org.awaitility", "awaitility").versionRef("awaitility-version")
      library("besu-internal", "org.hyperledger.besu.internal", "crypto")
          .versionRef("besu-internal-version")
      library("commons-collections4", "org.apache.commons", "commons-collections4")
          .versionRef("commons-collections4-version")
      library("hamcrest", "org.hamcrest", "hamcrest").versionRef("hamcrest-version")
      library("helidon-grpc-client", "io.helidon.grpc", "helidon-grpc-client")
          .versionRef("helidon-version")
      library("json", "org.json", "json").versionRef("json-version")
      library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit5-version")
      library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api")
          .versionRef("junit5-version")
      library("junit-jupiter-params", "org.junit.jupiter", "junit-jupiter-params")
          .versionRef("junit5-version")
      library("junit-pioneer", "org.junit-pioneer", "junit-pioneer")
          .versionRef("junit-pioneer-version")
      library("mockito-inline", "org.mockito", "mockito-inline").versionRef("mockito-version")
      library("mockito-jupiter", "org.mockito", "mockito-junit-jupiter")
          .versionRef("mockito-version")
      library("picocli", "info.picocli", "picocli").versionRef("picocli-version")
      library("snakeyaml", "org.yaml", "snakeyaml").versionRef("snakeyaml-version")
      library("testcontainers-core", "org.testcontainers", "testcontainers")
          .versionRef("testcontainers-version")
      library("testcontainers-junit", "org.testcontainers", "junit-jupiter")
          .versionRef("testcontainers-version")
      library("classgraph", "io.github.classgraph", "classgraph").versionRef("classgraph-version")
      library("assertj-core", "org.assertj", "assertj-core").versionRef("assertj-version")
    }
  }
}
