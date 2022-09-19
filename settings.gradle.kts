/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
    id("com.gradle.enterprise").version("3.10.3")
}

rootProject.name = "hedera-services"

// Define the subprojects
include(":hapi-utils")
include(":hapi-fees")
include(":hedera-node")
include(":hedera-node:hedera-app")
include(":hedera-node:hedera-app-api")
include(":test-clients")

// Enable Gradle Build Scan
gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

// Define the library catalogs available for projects to make use of
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    versionCatalogs {
        // The libs of this catalog are the **ONLY** ones that are authorized to be part of the runtime
        // distribution. These libs can be depended on during compilation, or bundled as part of runtime.
        create("libs") {
            // Definition of version numbers for all libraries
            version("besu-version", "22.7.1")
            version("besu-native-version", "0.5.0")
            version("bouncycastle-version", "1.70")
            version("caffeine-version", "3.0.6")
            version("commons-codec-version", "1.15")
            version("commons-io-version", "2.11.0")
            version("commons-lang3-version", "3.12.0")
            version("dagger-version", "2.42")
            version("eddsa-version", "0.3.0")
            version("grpc-version", "1.39.0")
            version("guava-version", "31.1-jre")
            version("hapi-version", "0.30.0-SNAPSHOT")
            version("headlong-version", "6.1.1")
            version("jackson-version", "2.12.6.1")
            version("javax-annotation-version", "1.3.2")
            version("javax-inject-version", "1")
            version("jetbrains-annotation-version", "16.0.2")
            version("log4j-version", "2.17.2")
            version("netty-version", "4.1.66.Final")
            version("protobuf-java-version", "3.19.4")
            version("swirlds-version", "0.30.0")
            version("tuweni-version", "2.2.0")

            // List of bundles provided for us. When applicable, favor using these over individual libraries.
            // Use when you need to use Besu
            bundle("besu", listOf("besu-bls12-381", "besu-evm", "besu-datatypes", "besu-secp256k1", "tuweni-units"))
            // Use when you need to use bouncy castle
            bundle("bouncycastle", listOf("bouncycastle-bcprov-jdk15on", "bouncycastle-bcpkix-jdk15on"))
            // Use when you need to make use of dependency injection.
            bundle("di", listOf("javax-inject", "dagger-api"))
            // Use when you need logging
            bundle("logging", listOf("log4j-api", "log4j-core"))
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
                    "swirlds-virtualmap"
                )
            )

            // Define the individual libraries
            library("besu-bls12-381", "org.hyperledger.besu", "bls12-381").versionRef("besu-native-version")
            library("besu-secp256k1", "org.hyperledger.besu", "secp256k1").versionRef("besu-native-version")
            library("besu-evm", "org.hyperledger.besu", "evm").versionRef("besu-version")
            library("besu-datatypes", "org.hyperledger.besu", "besu-datatypes").versionRef("besu-version")
            library("bouncycastle-bcprov-jdk15on", "org.bouncycastle", "bcprov-jdk15on").versionRef("bouncycastle-version")
            library("bouncycastle-bcpkix-jdk15on", "org.bouncycastle", "bcpkix-jdk15on").versionRef("bouncycastle-version")
            library("caffeine", "com.github.ben-manes.caffeine", "caffeine").versionRef("caffeine-version")
            library("commons-codec", "commons-codec", "commons-codec").versionRef("commons-codec-version")
            library("commons-io", "commons-io", "commons-io").versionRef("commons-io-version")
            library("commons-lang3", "org.apache.commons", "commons-lang3").versionRef("commons-lang3-version")
            library("dagger-api", "com.google.dagger", "dagger").versionRef("dagger-version")
            library("dagger-compiler", "com.google.dagger", "dagger-compiler").versionRef("dagger-version")
            library("eddsa", "net.i2p.crypto", "eddsa").versionRef("eddsa-version")
            library("grpc-protobuf", "io.grpc", "grpc-protobuf").versionRef("grpc-version")
            library("grpc-netty", "io.grpc", "grpc-netty").versionRef("grpc-version")
            library("guava", "com.google.guava", "guava").versionRef("guava-version")
            library("hapi", "com.hedera.hashgraph", "hedera-protobuf-java-api").versionRef("hapi-version")
            library("headlong", "com.esaulpaugh", "headlong").versionRef("headlong-version")
            library("jackson", "com.fasterxml.jackson.core", "jackson-databind").versionRef("jackson-version")
            library("javax-annotation", "javax.annotation", "javax.annotation-api").versionRef("javax-annotation-version")
            library("javax-inject", "javax.inject", "javax.inject").versionRef("javax-inject-version")
            library("jetbrains-annotation", "org.jetbrains", "annotations").versionRef("jetbrains-annotation-version")
            library("log4j-api", "org.apache.logging.log4j", "log4j-api").versionRef("log4j-version")
            library("log4j-core", "org.apache.logging.log4j", "log4j-core").versionRef("log4j-version")
            library("netty-transport-native-epoll", "io.netty", "netty-transport-native-epoll").versionRef("netty-version")
            library("netty-handler", "io.netty", "netty-handler").versionRef("netty-version")
            library("protobuf-java", "com.google.protobuf", "protobuf-java").versionRef("protobuf-java-version")
            library("swirlds-common", "com.swirlds", "swirlds-common").versionRef("swirlds-version")
            library("swirlds-platform-core", "com.swirlds", "swirlds-platform-core").versionRef("swirlds-version")
            library("swirlds-fchashmap", "com.swirlds", "swirlds-fchashmap").versionRef("swirlds-version")
            library("swirlds-merkle", "com.swirlds", "swirlds-merkle").versionRef("swirlds-version")
            library("swirlds-fcqueue", "com.swirlds", "swirlds-fcqueue").versionRef("swirlds-version")
            library("swirlds-jasperdb", "com.swirlds", "swirlds-jasperdb").versionRef("swirlds-version")
            library("swirlds-virtualmap", "com.swirlds", "swirlds-virtualmap").versionRef("swirlds-version")
            library("tuweni-units", "org.apache.tuweni", "tuweni-units").versionRef("tuweni-version")
        }

        // The libs of this catalog can be used for test or build uses.
        create("testLibs") {
            version("awaitility-version", "4.2.0")
            version("besu-internal-version", "22.1.1")
            version("commons-collections4-version", "4.4")
            version("ethereumj-version", "1.12.0-v0.5.0")
            version("hamcrest-version", "2.2")
            version("json-version", "20210307")
            version("junit5-version", "5.8.2")
            version("mockito-version", "4.6.1")
            version("picocli-version", "4.6.3")
            version("snakeyaml-version", "1.26")
            version("testcontainers-version", "1.17.2")
            version("truth-java8-extension-version", "1.1.3")

            bundle("junit5", listOf("junit-jupiter-api", "junit-jupiter-params", "junit-jupiter"))
            bundle("mockito", listOf("mockito-core", "mockito-jupiter"))
            bundle("testcontainers", listOf("testcontainers-core", "testcontainers-junit"))
            bundle("testing", listOf("junit-jupiter", "junit-jupiter-api", "junit-jupiter-params", "mockito-core", "mockito-jupiter", "hamcrest", "awaitility", "truth-java8-extension"))

            library("awaitility", "org.awaitility", "awaitility").versionRef("awaitility-version")
            library("besu-internal", "org.hyperledger.besu.internal", "crypto").versionRef("besu-internal-version")
            library("commons-collections4", "org.apache.commons", "commons-collections4").versionRef("commons-collections4-version")
            library("ethereumj", "com.hedera.hashgraph", "ethereumj-core").versionRef("ethereumj-version")
            library("hamcrest", "org.hamcrest", "hamcrest").versionRef("hamcrest-version")
            library("json", "org.json", "json").versionRef("json-version")
            library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit5-version")
            library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api").versionRef("junit5-version")
            library("junit-jupiter-params", "org.junit.jupiter", "junit-jupiter-params").versionRef("junit5-version")
            library("mockito-core", "org.mockito", "mockito-core").versionRef("mockito-version")
            library("mockito-jupiter", "org.mockito", "mockito-junit-jupiter").versionRef("mockito-version")
            library("picocli", "info.picocli", "picocli").versionRef("picocli-version")
            library("snakeyaml", "org.yaml", "snakeyaml").versionRef("snakeyaml-version")
            library("testcontainers-core", "org.testcontainers", "testcontainers").versionRef("testcontainers-version")
            library("testcontainers-junit", "org.testcontainers", "junit-jupiter").versionRef("testcontainers-version")
            library("truth-java8-extension", "com.google.truth.extensions", "truth-java8-extension").versionRef("truth-java8-extension-version")
        }
    }
}
