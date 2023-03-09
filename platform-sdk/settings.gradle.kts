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

plugins { id("com.gradle.enterprise").version("3.10.3") }

rootProject.name = "swirlds-platform"

include(":swirlds")

include(":swirlds-logging")

include(":swirlds-common")

include(":swirlds-config-api")

include(":swirlds-config-impl")

include(":swirlds-config-benchmark")

include(":swirlds-fchashmap")

include(":swirlds-fcqueue")

include(":swirlds-merkle")

include(":swirlds-jasperdb")

include(":swirlds-virtualmap")

include(":swirlds-platform-core")

include(":swirlds-cli")

include(":platform-apps")

project(":platform-apps").name = "swirlds-platform-apps"

include(":swirlds-platform-apps:demos:CryptocurrencyDemo")

include(":swirlds-platform-apps:demos:HashgraphDemo")

include(":swirlds-platform-apps:demos:HelloSwirldDemo")

include(":swirlds-platform-apps:demos:StatsDemo")

include(":swirlds-platform-apps:tests:ISSTestingTool")

include(":swirlds-platform-apps:tests:MigrationTestingTool")

include(":swirlds-platform-apps:tests:PlatformTestingTool")

include(":swirlds-platform-apps:tests:StatsSigningTestingTool")

include(":swirlds-benchmarks")

include(":swirlds-unit-tests")

include(":swirlds-unit-tests:common:swirlds-test-framework")

include(":swirlds-unit-tests:common:swirlds-common-test")

include(":swirlds-unit-tests:core:swirlds-platform-test")

include(":swirlds-unit-tests:structures:swirlds-merkle-test")

dependencyResolutionManagement {
  @Suppress("UnstableApiUsage")
  versionCatalogs {
    // The libs of this catalog are the **ONLY** ones that are authorized to be part of the runtime
    // distribution. These libs can be depended on during compilation, or bundled as part of
    // runtime.
    create("libs") {
      // Define the approved version numbers
      // Third-party dependency versions

      // Cryptography Libraries
      version("lazysodium-version", "5.1.1")
      version("bouncycastle-version", "1.70")

      // Apache Commons
      version("commons-lang3-version", "3.12.0")
      version("commons-io-version", "2.11.0")
      version("commons-codec-version", "1.15")
      version("commons-math3-version", "3.6.1")
      version("commons-collections4-version", "4.4")

      // Eclipse Commons
      version("eclipse-collections-version", "10.4.0")

      // Classgraph
      version("classgraph-version", "4.8.65")

      // Logging
      version("slf4j-version", "2.0.0")
      version("log4j-version", "2.17.2")

      // Parsers
      version("jackson-version", "2.13.3")

      // Network
      version("portmapper-version", "2.0.4")

      // JavaFX
      version("javafx-version", "17")

      // JNI
      version("resource-loader-version", "2.0.1")
      version("jna-version", "5.12.1")

      // Protobuf
      version("protobuf-version", "3.21.5")

      // Prometheus Java client
      version("prometheus-client", "0.16.0")

      // PicoCLI
      version("picocli-version", "4.6.3")

      version("spotbugs-version", "4.7.3")

      // List of bundles provided for us. When applicable, favor using these over individual
      // libraries.
      bundle("eclipse", listOf("eclipse-collections"))
      bundle("cryptography-core", listOf("lazysodium", "bc-provider", "bc-pkix"))
      bundle("cryptography-runtime", listOf("jna", "resource-loader"))
      bundle("logging-api", listOf("log4j-api", "slf4j-api"))
      bundle("logging-impl", listOf("log4j-core", "slf4j-nop"))
      bundle(
          "jackson",
          listOf("jackson-databind", "jackson-datatype-jsr310", "jackson-dataformat-yaml"))
      bundle("networking", listOf("portmapper"))
      bundle("javafx", listOf("javafx-base"))
      bundle("picocli", listOf("picocli"))

      // Define the individual libraries
      // Commons Bundle
      library("commons-lang3", "org.apache.commons", "commons-lang3")
          .versionRef("commons-lang3-version")
      library("commons-io", "commons-io", "commons-io").versionRef("commons-io-version")
      library("commons-codec", "commons-codec", "commons-codec").versionRef("commons-codec-version")
      library("commons-math3", "org.apache.commons", "commons-math3")
          .versionRef("commons-math3-version")
      library("commons-collections4", "org.apache.commons", "commons-collections4")
          .versionRef("commons-collections4-version")
      // Eclipse Bundle
      library("eclipse-collections", "org.eclipse.collections", "eclipse-collections")
          .versionRef("eclipse-collections-version")
      // Cryptography Bundle
      library("bc-provider", "org.bouncycastle", "bcprov-jdk15on")
          .versionRef("bouncycastle-version")
      library("bc-pkix", "org.bouncycastle", "bcpkix-jdk15on").versionRef("bouncycastle-version")
      library("lazysodium", "com.goterl", "lazysodium-java").versionRef("lazysodium-version")
      // Log4j Bundle
      library("log4j-api", "org.apache.logging.log4j", "log4j-api").versionRef("log4j-version")
      library("log4j-core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j-version")
      // Slf4j Bundle
      library("slf4j-api", "org.slf4j", "slf4j-api").versionRef("slf4j-version")
      library("slf4j-nop", "org.slf4j", "slf4j-nop").versionRef("slf4j-version")
      // Jackson Bundle
      library("jackson-databind", "com.fasterxml.jackson.core", "jackson-databind")
          .versionRef("jackson-version")
      library("jackson-datatype-joda", "com.fasterxml.jackson.datatype", "jackson-datatype-joda")
          .versionRef("jackson-version")
      library("jackson-datatype-guava", "com.fasterxml.jackson.datatype", "jackson-datatype-guava")
          .versionRef("jackson-version")
      library("jackson-datatype-jdk8", "com.fasterxml.jackson.datatype", "jackson-datatype-jdk8")
          .versionRef("jackson-version")
      library(
              "jackson-datatype-jsr310",
              "com.fasterxml.jackson.datatype",
              "jackson-datatype-jsr310")
          .versionRef("jackson-version")
      library(
              "jackson-dataformat-yaml",
              "com.fasterxml.jackson.dataformat",
              "jackson-dataformat-yaml")
          .versionRef("jackson-version")
      // Networking Bundle
      library("portmapper", "com.offbynull.portmapper", "portmapper")
          .versionRef("portmapper-version")
      // JavaFX Bundle
      library("javafx-base", "org.openjfx", "javafx-base").versionRef("javafx-version")
      // Misc
      library("classgraph", "io.github.classgraph", "classgraph").versionRef("classgraph-version")
      library("jna", "net.java.dev.jna", "jna").versionRef("jna-version")
      library("resource-loader", "com.goterl", "resource-loader")
          .versionRef("resource-loader-version")
      library("protobuf", "com.google.protobuf", "protobuf-java").versionRef("protobuf-version")
      library("prometheus-httpserver", "io.prometheus", "simpleclient_httpserver")
          .versionRef("prometheus-client")
      // PicoCLI Bundle
      library("picocli", "info.picocli", "picocli").versionRef("picocli-version")

      library("spotbugs-annotations", "com.github.spotbugs", "spotbugs-annotations")
          .versionRef("spotbugs-version")
    }

    create("testLibs") {
      // Define the approved version numbers
      // Third-party dependency versions

      // Test Frameworks
      version("junit-version", "5.9.0")

      // Mocking Frameworks
      version("mockito-version", "4.7.0")

      // Test Utils
      version("awaitility-version", "4.2.0")
      version("assertj-version", "3.23.1")
      version("truth-version", "1.1.3")

      // List of bundles provided for us. When applicable, favor using these over individual
      // libraries.
      bundle("junit", listOf("junit-jupiter", "junit-jupiter-api", "junit-jupiter-params"))
      bundle("mocking", listOf("mockito-core", "mockito-junit"))
      bundle("utils", listOf("awaitility", "assertj-core", "truth"))

      // Define the individual libraries
      // JUnit Bundle
      library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit-version")
      library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api")
          .versionRef("junit-version")
      library("junit-jupiter-params", "org.junit.jupiter", "junit-jupiter-params")
          .versionRef("junit-version")

      // Mocking Bundle
      library("mockito-core", "org.mockito", "mockito-core").versionRef("mockito-version")
      library("mockito-junit", "org.mockito", "mockito-junit-jupiter").versionRef("mockito-version")

      // Utils Bundle
      library("awaitility", "org.awaitility", "awaitility").versionRef("awaitility-version")
      library("assertj-core", "org.assertj", "assertj-core").versionRef("assertj-version")
      library("truth", "com.google.truth", "truth").versionRef("truth-version")
    }
  }
}

gradleEnterprise {
  buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
  }
}
