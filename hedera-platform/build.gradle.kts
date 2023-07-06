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
    id("com.hedera.hashgraph.platform")
}

val besuNativeVersion = "0.6.1"
val besuVersion = "23.1.2"
val bouncycastleVersion = "1.70"
val daggerVersion = "2.42"
val eclipseCollectionsVersion = "10.4.0"
val grpcVersion = "1.54.1"
val helidonVersion = "3.2.1"
val jacksonVersion = "2.13.5"
val log4jVersion = "2.17.1"
val mockitoVersion = "4.6.1"
val nettyVersion = "4.1.87.Final"
val protobufVersion = "3.21.7"
val swirldsVersion = "0.40.0-adhoc.x5482f0a8"
val systemStubsVersion = "2.0.2"
val testContainersVersion = "1.17.2"
val tuweniVersion = "2.2.0"

dependencies {
    api(enforcedPlatform("io.netty:netty-bom:$nettyVersion"))
}

dependencies.constraints {
    javaModuleDependencies {
        api(gav("awaitility", "4.2.0"))
        api(gav("com.fasterxml.jackson.core", jacksonVersion))
        api(gav("com.fasterxml.jackson.databind", jacksonVersion))
        api(gav("com.github.benmanes.caffeine", "3.0.6"))
        api(gav("com.github.docker.java.api", "3.2.13"))
        api(gav("com.github.spotbugs.annotations", "4.7.3"))
        api(gav("com.google.common", "31.1-jre"))
        api(gav("com.google.protobuf", protobufVersion))
        api(gav("com.google.protobuf.util", protobufVersion))
        api(gav("com.hedera.hashgraph.protobuf.java.api", "0.40.0-blocks-state-SNAPSHOT")) // TODO removed through other PR
        api(gav("com.hedera.pbj.runtime", "0.7.0"))
        api(gav("com.sun.jna", "5.12.1"))
        api(gav("com.swirlds.base", swirldsVersion))
        api(gav("com.swirlds.cli", swirldsVersion))
        api(gav("com.swirlds.common", swirldsVersion))
        api(gav("com.swirlds.config", swirldsVersion))
        api(gav("com.swirlds.fchashmap", swirldsVersion))
        api(gav("com.swirlds.fcqueue", swirldsVersion))
        api(gav("com.swirlds.jasperdb", swirldsVersion))
        api(gav("com.swirlds.logging", swirldsVersion))
        api(gav("com.swirlds.merkle", swirldsVersion))
        api(gav("com.swirlds.platform", swirldsVersion))
        api(gav("com.swirlds.test.framework", swirldsVersion))
        api(gav("com.swirlds.virtualmap", swirldsVersion))
        api(gav("dagger", daggerVersion))
        api(gav("dagger.compiler", daggerVersion))
        api(gav("grpc.netty", grpcVersion))
        api(gav("grpc.stub", grpcVersion))
        api(gav("headlong", "6.1.1"))
        api(gav("info.picocli", "4.6.3"))
        api(gav("io.github.classgraph", "4.8.65"))
        api(gav("io.grpc", helidonVersion))
        api(gav("io.helidon.grpc.client", helidonVersion))
        api(gav("io.helidon.grpc.core", helidonVersion))
        api(gav("io.helidon.grpc.server", helidonVersion))
        api(gav("io.netty.handler", nettyVersion))
        api(gav("io.netty.transport", nettyVersion))
        api(gav("io.netty.transport.classes.epoll", nettyVersion))
        api(gav("io.netty.transport.epoll", nettyVersion))
        api(gav("io.perfmark", "0.25.0"))
        api(gav("javax.inject", "1"))
        api(gav("net.i2p.crypto.eddsa", "0.3.0"))
        api(gav("org.antlr.antlr4.runtime", "4.11.1"))
        api(gav("org.apache.commons.codec", "1.15"))
        api(gav("org.apache.commons.collections4", "4.4"))
        api(gav("org.apache.commons.io", "2.11.0"))
        api(gav("org.apache.commons.lang3", "3.12.0"))
        api(gav("org.apache.logging.log4j", log4jVersion))
        api(gav("org.apache.logging.log4j.core", log4jVersion))
        api(gav("org.apache.logging.log4j.jul", log4jVersion))
        api(gav("org.assertj.core", "3.23.1"))
        api(gav("org.bouncycastle.pkix", bouncycastleVersion))
        api(gav("org.bouncycastle.provider", bouncycastleVersion))
        api(gav("org.eclipse.collections.api", eclipseCollectionsVersion))
        api(gav("org.eclipse.collections.impl", eclipseCollectionsVersion))
        api(gav("org.hamcrest", "2.2"))
        api(gav("org.hyperledger.besu.crypto", besuVersion))
        api(gav("org.hyperledger.besu.datatypes", besuVersion))
        api(gav("org.hyperledger.besu.evm", besuVersion))
        api(gav("org.hyperledger.besu.secp256k1", besuNativeVersion))
        api(gav("org.json", "20230227"))
        api(gav("org.junit.jupiter.api", "5.9.0"))
        api(gav("org.junitpioneer", "2.0.1"))
        api(gav("org.mockito", mockitoVersion))
        api(gav("org.mockito.inline", mockitoVersion))
        api(gav("org.mockito.junit.jupiter", mockitoVersion))
        api(gav("org.opentest4j", "1.2.0"))
        api(gav("org.slf4j", "2.0.3"))
        api(gav("org.testcontainers", testContainersVersion))
        api(gav("org.testcontainers.junit.jupiter", testContainersVersion))
        api(gav("org.yaml.snakeyaml", "1.33"))
        api(gav("tuweni.bytes", tuweniVersion))
        api(gav("tuweni.units", tuweniVersion))
        api(gav("uk.org.webcompere.systemstubs.core", systemStubsVersion))
        api(gav("uk.org.webcompere.systemstubs.jupiter", systemStubsVersion))
    }
}
