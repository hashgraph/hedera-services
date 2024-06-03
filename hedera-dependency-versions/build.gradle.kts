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
    id("com.hedera.gradle.versions")
}

dependencies {
    api(enforcedPlatform("io.netty:netty-bom:4.1.87.Final"))

    // Force commons compress version to close a security vulnerability
    api(javaModuleDependencies.gav("org.apache.commons.compress"))

    // forward logging from modules using SLF4J (e.g. 'org.hyperledger.besu.evm') to Log4J
    runtime(javaModuleDependencies.gav("org.apache.logging.log4j.slf4j2.impl"))
}

dependencies.constraints {
    api("org.awaitility:awaitility:4.2.0") {
        because("awaitility")
    }
    api("com.fasterxml.jackson.core:jackson-core:2.16.0") {
        because("com.fasterxml.jackson.core")
    }
    api("com.fasterxml.jackson.core:jackson-databind:2.16.0") {
        because("com.fasterxml.jackson.databind")
    }
    api("com.github.ben-manes.caffeine:caffeine:3.1.1") {
        because("com.github.benmanes.caffeine")
    }
    api("com.github.docker-java:docker-java-api:3.2.13") {
        because("com.github.docker.java.api")
    }
    api("com.github.spotbugs:spotbugs-annotations:4.7.3") {
        because("com.github.spotbugs.annotations")
    }
    api("com.google.auto.service:auto-service-annotations:1.1.1") {
        because("com.google.auto.service")
    }
    api("com.google.auto.service:auto-service:1.1.1") {
        because("com.google.auto.service.processor")
    }
    api("com.google.guava:guava:31.1-jre") {
        because("com.google.common")
    }
    api("com.google.jimfs:jimfs:1.2") {
        because("com.google.jimfs")
    }
    api("com.google.protobuf:protobuf-java:3.21.7") {
        because("com.google.protobuf")
    }
    api("com.google.protobuf:protobuf-java-util:3.21.7") {
        because("com.google.protobuf.util")
    }
    api("com.hedera.pbj:pbj-runtime:0.8.9") {
        because("com.hedera.pbj.runtime")
    }
    api("com.squareup:javapoet:1.13.0") {
        because("com.squareup.javapoet")
    }
    api("net.java.dev.jna:jna:5.12.1") {
        because("com.sun.jna")
    }
    api("com.google.dagger:dagger:2.42") {
        because("dagger")
    }
    api("com.google.dagger:dagger-compiler:2.42") {
        because("dagger.compiler")
    }
    api("io.grpc:grpc-netty:1.54.1") {
        because("grpc.netty")
    }
    api("io.grpc:grpc-protobuf:1.54.1") {
        because("grpc.protobuf")
    }
    api("io.grpc:grpc-stub:1.54.1") {
        because("grpc.stub")
    }
    api("com.esaulpaugh:headlong:6.1.1") {
        because("headlong")
    }
    api("info.picocli:picocli:4.6.3") {
        because("info.picocli")
    }
    api("io.github.classgraph:classgraph:4.8.65") {
        because("io.github.classgraph")
    }
    api("io.helidon.grpc:io.grpc:3.2.1") {
        because("io.grpc")
    }
    api("io.netty:netty-handler:4.1.87.Final") {
        because("io.netty.handler")
    }
    api("io.netty:netty-transport:4.1.87.Final") {
        because("io.netty.transport")
    }
    api("io.netty:netty-transport-classes-epoll:4.1.87.Final") {
        because("io.netty.transport.classes.epoll")
    }
    api("io.perfmark:perfmark-api:0.25.0") {
        because("io.perfmark")
    }
    api("io.prometheus:simpleclient:0.16.0") {
        because("io.prometheus.simpleclient")
    }
    api("io.prometheus:simpleclient_httpserver:0.16.0") {
        because("io.prometheus.simpleclient.httpserver")
    }
    api("jakarta.inject:jakarta.inject-api:2.0.1") {
        because("jakarta.inject")
    }
    api("javax.annotation:javax.annotation-api:1.3.2") {
        because("java.annotation")
    }
    api("javax.inject:javax.inject:1") {
        because("javax.inject")
    }
    api("com.goterl:lazysodium-java:5.1.1") {
        because("lazysodium.java")
    }
    api("net.i2p.crypto:eddsa:0.3.0") {
        because("net.i2p.crypto.eddsa")
    }
    api("org.antlr:antlr4-runtime:4.13.1") {
        because("org.antlr.antlr4.runtime")
    }
    api("commons-codec:commons-codec:1.15") {
        because("org.apache.commons.codec")
    }
    api("org.apache.commons:commons-collections4:4.4") {
        because("org.apache.commons.collections4")
    }
    api("commons-io:commons-io:2.15.1") {
        because("org.apache.commons.io")
    }
    api("org.apache.commons:commons-lang3:3.14.0") {
        because("org.apache.commons.lang3")
    }
    api("org.apache.commons:commons-compress:1.26.0") {
        because("org.apache.commons.compress")
    }
    api("org.apache.logging.log4j:log4j-api:2.21.1") {
        because("org.apache.logging.log4j")
    }
    api("org.apache.logging.log4j:log4j-core:2.21.1") {
        because("org.apache.logging.log4j.core")
    }
    api("org.apache.logging.log4j:log4j-slf4j2-impl:2.21.1") {
        because("org.apache.logging.log4j.slf4j2.impl")
    }
    api("org.assertj:assertj-core:3.23.1") {
        because("org.assertj.core")
    }
    api("org.bouncycastle:bcpkix-jdk18on:1.78") {
        because("org.bouncycastle.pkix")
    }
    api("org.bouncycastle:bcprov-jdk18on:1.78") {
        because("org.bouncycastle.provider")
    }
    api("org.eclipse.collections:eclipse-collections-api:10.4.0") {
        because("org.eclipse.collections.api")
    }
    api("org.eclipse.collections:eclipse-collections:10.4.0") {
        because("org.eclipse.collections.impl")
    }
    api("org.hamcrest:hamcrest:2.2") {
        because("org.hamcrest")
    }
    api("org.hyperledger.besu:besu-datatypes:24.3.3") {
        because("org.hyperledger.besu.datatypes")
    }
    api("org.hyperledger.besu:evm:24.3.3") {
        because("org.hyperledger.besu.evm")
    }
    api("org.hyperledger.besu:secp256k1:0.8.2") {
        because("org.hyperledger.besu.nativelib.secp256k1")
    }
    api("org.json:json:20231013") {
        because("org.json")
    }
    api("org.junit.jupiter:junit-jupiter-api:5.10.2") {
        because("org.junit.jupiter.api")
    }
    api("org.junit-pioneer:junit-pioneer:2.0.1") {
        because("org.junitpioneer")
    }
    api("org.mockito:mockito-core:5.8.0") {
        because("org.mockito")
    }
    api("org.mockito:mockito-junit-jupiter:5.8.0") {
        because("org.mockito.junit.jupiter")
    }
    api("org.opentest4j:opentest4j:1.2.0") {
        because("org.opentest4j")
    }
    api("org.testcontainers:testcontainers:1.17.2") {
        because("org.testcontainers")
    }
    api("org.testcontainers:junit-jupiter:1.17.2") {
        because("org.testcontainers.junit.jupiter")
    }
    api("org.yaml:snakeyaml:2.2") {
        because("org.yaml.snakeyaml")
    }
    api("io.tmio:tuweni-bytes:2.4.2") {
        because("tuweni.bytes")
    }
    api("io.tmio:tuweni-units:2.4.2") {
        because("tuweni.units")
    }
    api("uk.org.webcompere:system-stubs-core:2.1.5") {
        because("uk.org.webcompere.systemstubs.core")
    }
    api("uk.org.webcompere:system-stubs-jupiter:2.1.5") {
        because("uk.org.webcompere.systemstubs.jupiter")
    }
}
