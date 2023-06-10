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
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
    }
}

plugins {
    id("com.gradle.enterprise").version("3.11.4")
    // Use GIT plugin to clone HAPI protobuf files
    // See documentation https://melix.github.io/includegit-gradle-plugin/latest/index.html
    id("me.champeau.includegit").version("0.1.6")
}

includeBuild(".") // https://github.com/gradlex-org/java-module-dependencies/issues/26

include(":hedera-node")

include(":node-app-service-network-admin", "hedera-network-admin-service")

include(":node-app-service-network-admin-impl", "hedera-network-admin-service-impl")

include(":node-app-service-consensus", "hedera-consensus-service")

include(":node-app-service-consensus-impl", "hedera-consensus-service-impl")

include(":node-app-service-file", "hedera-file-service")

include(":node-app-service-file-impl", "hedera-file-service-impl")

include(":node-app-service-schedule", "hedera-schedule-service")

include(":node-app-service-schedule-impl", "hedera-schedule-service-impl")

include(":node-app-service-contract", "hedera-smart-contract-service")

include(":node-app-service-contract-impl", "hedera-smart-contract-service-impl")

include(":node-app-service-token", "hedera-token-service")

include(":node-app-service-token-impl", "hedera-token-service-impl")

include(":node-app-service-util", "hedera-util-service")

include(":node-app-service-util-impl", "hedera-util-service-impl")

include(":node-app-hapi-utils", "hapi-utils")

include(":node-app-hapi-fees", "hapi-fees")

include(":node-hapi", "hapi")

include(":node-config", "hedera-config")

include(":node-app", "hedera-app")

include(":node-app-spi", "hedera-app-spi")

include(":node-app-service-evm", "hedera-evm")

include(":node-app-service-evm-impl", "hedera-evm-impl")

include(":node-app-service-mono", "hedera-mono-service")

include(":services-cli", "cli-clients")

include(":hedera-node:test-clients")

fun include(name: String, path: String) {
    include(":hedera-node$name")
    project(":hedera-node$name").projectDir = File(rootDir, "hedera-node/$path")
}

// Enable Gradle Build Scan
gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

// The HAPI API version to use for Protobuf sources. This can be a tag or branch
// name from the hedera-protobufs GIT repo.
val hapiProtoVersion = "0.40.0-blocks-state-SNAPSHOT"
val hapiProtoBranchOrTag = "add-pbj-types-for-state" // hapiProtoVersion

gitRepositories {
    checkoutsDirectory.set(File(rootDir, "hedera-node/hapi"))
    // check branch in repo for updates every second
    refreshIntervalMillis.set(1000)
    include("hedera-protobufs") {
        uri.set("https://github.com/hashgraph/hedera-protobufs.git")
        // HAPI repo version
        tag.set(hapiProtoBranchOrTag)
        // do not load project from repo
        autoInclude.set(false)
    }
}

// Define the library catalogs available for projects to make use of
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    versionCatalogs {
        // The libs of this catalog are the **ONLY** ones that are authorized to be part of the
        // runtime
        // distribution. These libs can be depended on during compilation, or bundled as part of
        // runtime.
        create("libs") {
            val besuNativeVersion = "0.6.1"
            val besuVersion = "23.1.2"
            val bouncycastleVersion = "1.70"
            val daggerVersion = "2.42"
            val eclipseCollectionsVersion = "10.4.0"
            val helidonVersion = "3.0.2"
            val ioGrpcVersion = "1.45.1"
            val jacksonVersion = "2.13.3"
            val log4jVersion = "2.17.1"
            val mockitoVersion = "4.6.1"
            val swirldsVersion = "0.39.0-alpha.3"
            val systemStubsVersion = "2.0.2"
            val testContainersVersion = "1.17.2"
            val tuweniVersion = "2.2.0"

            version("awaitility", "4.2.0")
            version("com.fasterxml.jackson.core", jacksonVersion)
            version("com.fasterxml.jackson.databind", jacksonVersion)
            version("com.github.benmanes.caffeine", "3.0.6")
            version("com.github.docker.java.api", "3.2.13")
            version("com.github.spotbugs.annotations", "4.7.3")
            version("com.google.common", "31.1-jre")
            version("com.google.protobuf", "3.19.4")
            version("com.google.protobuf.util", "3.19.2")
            version("com.hedera.pbj.runtime", "0.6.0")
            version("com.sun.jna", "5.12.1")
            version("com.swirlds.base", swirldsVersion)
            version("com.swirlds.cli", swirldsVersion)
            version("com.swirlds.common", swirldsVersion)
            version("com.swirlds.config", swirldsVersion)
            version("com.swirlds.fchashmap", swirldsVersion)
            version("com.swirlds.fcqueue", swirldsVersion)
            version("com.swirlds.jasperdb", swirldsVersion)
            version("com.swirlds.logging", swirldsVersion)
            version("com.swirlds.merkle", swirldsVersion)
            version("com.swirlds.platform", swirldsVersion)
            version("com.swirlds.test.framework", swirldsVersion)
            version("com.swirlds.virtualmap", swirldsVersion)
            version("dagger", daggerVersion)
            version("dagger.compiler", daggerVersion)
            version("grpc.protobuf", ioGrpcVersion)
            version("grpc.stub", ioGrpcVersion)
            version("headlong", "6.1.1")
            version("info.picocli", "4.6.3")
            version("io.github.classgraph", "4.8.65")
            version("io.grpc", helidonVersion)
            version("io.helidon.grpc.client", helidonVersion)
            version("io.helidon.grpc.core", helidonVersion)
            version("io.helidon.grpc.server", helidonVersion)
            version("io_helidon_common_configurable", helidonVersion)
            version("java.annotation", "3.0.2")
            version("javax.inject", "1")
            version("net.i2p.crypto.eddsa", "0.3.0")
            version("org.antlr.antlr4.runtime", "4.11.1")
            version("org.apache.commons.codec", "1.15")
            version("org.apache.commons.collections4", "4.4")
            version("org.apache.commons.io", "2.11.0")
            version("org.apache.commons.lang3", "3.12.0")
            version("org.apache.logging.log4j", log4jVersion)
            version("org.apache.logging.log4j.core", log4jVersion)
            version("org.assertj.core", "3.23.1")
            version("org.bouncycastle.pkix", bouncycastleVersion)
            version("org.bouncycastle.provider", bouncycastleVersion)
            version("org.eclipse.collections.api", eclipseCollectionsVersion)
            version("org.eclipse.collections.impl", eclipseCollectionsVersion)
            version("org.hamcrest", "2.2")
            version("org.hyperledger.besu.crypto", besuVersion)
            version("org.hyperledger.besu.datatypes", besuVersion)
            version("org.hyperledger.besu.evm", besuVersion)
            version("org.hyperledger.besu.secp256k1", besuNativeVersion)
            version("org.json", "20210307")
            version("org.junit.jupiter.api", "5.9.0")
            version("org.junitpioneer", "2.0.1")
            version("org.mockito", mockitoVersion)
            version("org.mockito.inline", mockitoVersion)
            version("org.mockito.junit.jupiter", mockitoVersion)
            version("org.opentest4j", "1.2.0")
            version("org.slf4j", "2.0.3")
            version("org.testcontainers", testContainersVersion)
            version("org.testcontainers.junit.jupiter", testContainersVersion)
            version("tuweni.bytes", tuweniVersion)
            version("tuweni.units", tuweniVersion)
            version("uk.org.webcompere.systemstubs.core", systemStubsVersion)
            version("uk.org.webcompere.systemstubs.jupiter", systemStubsVersion)

            version("hapi-proto", hapiProtoVersion)

            plugin("pbj", "com.hedera.pbj.pbj-compiler").version("0.6.0")
        }
    }
}
