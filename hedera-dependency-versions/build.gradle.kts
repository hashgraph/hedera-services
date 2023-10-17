/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.versions")
}

val besuNativeVersion = "0.8.2"
val besuVersion = "23.10.0"
val bouncycastleVersion = "1.70"
val daggerVersion = "2.42"
val eclipseCollectionsVersion = "10.4.0"
val grpcVersion = "1.54.1"
val helidonVersion = "3.2.1"
val jacksonVersion = "2.13.5"
val log4jVersion = "2.17.2"
val mockitoVersion = "4.11.0"
val nettyVersion = "4.1.87.Final"
val prometheusVersion = "0.16.0"
val protobufVersion = "3.21.7"
val systemStubsVersion = "2.0.2"
val testContainersVersion = "1.17.2"
val tuweniVersion = "2.3.1"

dependencies {
    api(enforcedPlatform("io.netty:netty-bom:$nettyVersion"))
}

moduleInfo {
    version("awaitility", "4.2.0")
    version("com.fasterxml.jackson.core", jacksonVersion)
    version("com.fasterxml.jackson.databind", jacksonVersion)
    version("com.github.benmanes.caffeine", "3.1.1")
    version("com.github.docker.java.api", "3.2.13")
    version("com.github.spotbugs.annotations", "4.7.3")
    version("com.google.common", "31.1-jre")
    version("com.google.jimfs", "1.2")
    version("com.google.protobuf", protobufVersion)
    version("com.google.protobuf.util", protobufVersion)
    version("com.google.truth", "1.1.3")
    version("com.hedera.pbj.runtime", "0.7.4")
    version("com.sun.jna", "5.12.1")
    version("dagger", daggerVersion)
    version("dagger.compiler", daggerVersion)
    version("grpc.netty", grpcVersion)
    version("grpc.protobuf", grpcVersion)
    version("grpc.stub", grpcVersion)
    version("headlong", "6.1.1")
    version("info.picocli", "4.6.3")
    version("io.github.classgraph", "4.8.65")
    version("io.grpc", helidonVersion)
    version("io.netty.handler", nettyVersion)
    version("io.netty.transport", nettyVersion)
    version("io.netty.transport.classes.epoll", nettyVersion)
    version("io.netty.transport.epoll", nettyVersion)
    version("io.perfmark", "0.25.0")
    version("io.prometheus.simpleclient", prometheusVersion)
    version("io.prometheus.simpleclient.httpserver", prometheusVersion)
    version("java.annotation", "1.3.2")
    version("javafx.base", "17")
    version("javax.inject", "1")
    version("lazysodium.java", "5.1.1")
    version("net.i2p.crypto.eddsa", "0.3.0")
    version("org.antlr.antlr4.runtime", "4.11.1")
    version("org.apache.commons.codec", "1.15")
    version("org.apache.commons.collections4", "4.4")
    version("org.apache.commons.io", "2.11.0")
    version("org.apache.commons.lang3", "3.12.0")
    version("org.apache.commons.math3", "3.2")
    version("org.apache.logging.log4j", log4jVersion)
    version("org.apache.logging.log4j.core", log4jVersion)
    version("org.apache.logging.log4j.jul", log4jVersion)
    version("org.assertj.core", "3.23.1")
    version("org.bouncycastle.pkix", bouncycastleVersion)
    version("org.bouncycastle.provider", bouncycastleVersion)
    version("org.eclipse.collections.api", eclipseCollectionsVersion)
    version("org.eclipse.collections.impl", eclipseCollectionsVersion)
    version("org.hamcrest", "2.2")
    version("org.hyperledger.besu.internal.crypto", besuVersion)
    version("org.hyperledger.besu.datatypes", besuVersion)
    version("org.hyperledger.besu.evm", besuVersion)
    version("org.hyperledger.besu.secp256k1", besuNativeVersion)
    version("org.json", "20230227")
    version("org.junit.jupiter.api", "5.9.1")
    version("org.junit.platform.engine", "1.9.1")
    version("org.junitpioneer", "2.0.1")
    version("org.mockito", mockitoVersion)
    version("org.mockito.inline", mockitoVersion)
    version("org.mockito.junit.jupiter", mockitoVersion)
    version("org.opentest4j", "1.2.0")
    version("org.slf4j", "2.0.3")
    version("org.testcontainers", testContainersVersion)
    version("org.testcontainers.junit.jupiter", testContainersVersion)
    version("org.yaml.snakeyaml", "1.33")
    version("portmapper", "2.0.4")
    version("tuweni.bytes", tuweniVersion)
    version("tuweni.units", tuweniVersion)
    version("uk.org.webcompere.systemstubs.core", systemStubsVersion)
    version("uk.org.webcompere.systemstubs.jupiter", systemStubsVersion)
    version("jakarta.inject", "2.0.1")
}
