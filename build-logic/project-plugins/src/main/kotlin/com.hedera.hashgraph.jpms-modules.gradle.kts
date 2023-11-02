/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.hedera.hashgraph.gradlebuild.rules.*

plugins {
    id("org.gradlex.java-ecosystem-capabilities")
    id("org.gradlex.extra-java-module-info")
}

dependencies.components {
    // TODO remove, once a new version of 'com.hedera.pbj.runtime' has been
    //  published with fix from https://github.com/hashgraph/pbj/pull/92
    withModule("com.hedera.pbj:pbj-runtime") {
        allVariants { withDependencies { removeAll { it.name != "antlr4-runtime" } } }
    }

    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-netty")
    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-protobuf")
    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-protobuf-lite")
    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-services")
    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-stub")
    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-testing")

    withModule<RemoveAnnotationLibrariesMetadataRule>("com.github.ben-manes.caffeine:caffeine")
    withModule<RemoveAnnotationLibrariesMetadataRule>("com.github.spotbugs:spotbugs-annotations")
    withModule<RemoveAnnotationLibrariesMetadataRule>("com.google.dagger:dagger-compiler")
    withModule<RemoveAnnotationLibrariesMetadataRule>("com.google.dagger:dagger-spi")
    withModule<RemoveAnnotationLibrariesMetadataRule>("com.google.guava:guava")
    withModule<RemoveAnnotationLibrariesMetadataRule>("com.google.protobuf:protobuf-java-util")
    withModule<RemoveAnnotationLibrariesMetadataRule>("io.helidon.grpc:io.grpc")
    withModule<RemoveAnnotationLibrariesMetadataRule>("org.apache.tuweni:tuweni-bytes")
    withModule<RemoveAnnotationLibrariesMetadataRule>("org.apache.tuweni:tuweni-units")

    withModule<IoNettyNativeEpollMetadataRule>("io.netty:netty-transport-native-epoll")

    withModule<IoPrometheusSimpleclientMetadataRule>("io.prometheus:simpleclient")

    withModule<RemoveKotlinStdlibCommonMetadataRule>("org.jetbrains.kotlin:kotlin-stdlib")
}

extraJavaModuleInfo {
    module("io.grpc:grpc-netty", "grpc.netty") {
        exportAllPackages()
        requireAllDefinedDependencies()
        requires("java.logging")
    }
    module("io.grpc:grpc-stub", "grpc.stub") {
        exportAllPackages()
        requireAllDefinedDependencies()
        requires("java.logging")
    }
    module("io.grpc:grpc-testing", "grpc.testing") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.grpc:grpc-services", "grpc.services") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.grpc:grpc-protobuf", "grpc.protobuf") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.grpc:grpc-protobuf-lite", "grpc.protobuf.lite") {
        exportAllPackages()
        requireAllDefinedDependencies()
        requires("com.google.protobuf")
    }
    module("javax.annotation:javax.annotation-api", "java.annotation") {
        exportAllPackages()
        // no dependencies
    }
    module("com.github.spotbugs:spotbugs-annotations", "com.github.spotbugs.annotations") {
        exportAllPackages()
        // no dependencies - see RemoveFindbugsAnnotationsMetadataRule
    }
    module("com.google.protobuf:protobuf-java", "com.google.protobuf") {
        exportAllPackages()
        requireAllDefinedDependencies()
        requires("java.logging")
    }
    module("com.google.guava:guava", "com.google.common") {
        exportAllPackages()
        requireAllDefinedDependencies()
        requires("java.logging")
    }
    module("com.google.guava:failureaccess", "com.google.guava.failureaccess") {
        exportAllPackages()
        // no dependencies
    }
    module("com.google.api.grpc:proto-google-common-protos", "com.google.api.grpc.common") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("com.google.dagger:dagger", "dagger") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.perfmark:perfmark-api", "io.perfmark") {
        exportAllPackages()
        // no dependencies
    }
    module("javax.inject:javax.inject", "javax.inject") {
        exportAllPackages()
        // no dependencies
    }
    module("org.apache.commons:commons-lang3", "org.apache.commons.lang3") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("commons-codec:commons-codec", "org.apache.commons.codec") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("commons-io:commons-io", "org.apache.commons.io") {
        exportAllPackages()
        // no dependencies
    }
    module("org.apache.commons:commons-math3", "org.apache.commons.math3") {
        exportAllPackages()
        // no dependencies
    }
    module("org.apache.commons:commons-collections4", "org.apache.commons.collections4") {
        exportAllPackages()
        // no dependencies
    }
    module("com.esaulpaugh:headlong", "headlong") {
        exportAllPackages()
        // no dependencies
    }
    module("org.connid:framework", "org.connid.framework") {
        exportAllPackages()
        // no dependencies
    }
    module("org.connid:framework-internal", "org.connid.framework.internal") {
        exportAllPackages()
        requires("org.connid.framework") // this is missing in POM
    }
    module("org.jetbrains:annotations", "org.jetbrains.annotations") {
        exportAllPackages()
        // no dependencies
    }
    module("io.tmio:tuweni-units", "tuweni.units") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.tmio:tuweni-bytes", "tuweni.bytes") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("net.i2p.crypto:eddsa", "net.i2p.crypto.eddsa") {
        exportAllPackages()
        // no dependencies
    }
    module("io.netty:netty-codec-http", "io.netty.codec.http") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-codec-http2", "io.netty.codec.http2") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-codec-socks", "io.netty.codec.socks") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-handler-proxy", "io.netty.handler.proxy") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-transport-native-unix-common", "io.netty.transport.unix.common") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-buffer", "io.netty.buffer") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-codec", "io.netty.codec") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-common", "io.netty.common") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-handler", "io.netty.handler") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-resolver", "io.netty.resolver") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-transport", "io.netty.transport") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.netty:netty-transport-classes-epoll", "io.netty.transport.classes.epoll") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.antlr:antlr4-runtime", "org.antlr.antlr4.runtime") {
        exportAllPackages()
        // no dependencies
    }
    module("org.yaml:snakeyaml", "org.yaml.snakeyaml") {
        exportAllPackages()
        // no dependencies
    }
    module("org.hyperledger.besu.internal:algorithms", "org.hyperledger.besu.internal.algorithms") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.hyperledger.besu.internal:rlp", "org.hyperledger.besu.internal.rlp") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.hyperledger.besu:arithmetic", "org.hyperledger.besu.arithmetic") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.hyperledger.besu:blake2bf", "org.hyperledger.besu.blake2bf") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.hyperledger.besu:bls12-381", "org.hyperledger.besu.bls12.for381") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.hyperledger.besu:besu-datatypes", "org.hyperledger.besu.datatypes") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.hyperledger.besu:evm", "org.hyperledger.besu.evm") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.hyperledger.besu:plugin-api", "org.hyperledger.besu.plugin.api") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.hyperledger.besu:secp256k1", "org.hyperledger.besu.secp256k1") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.hyperledger.besu:secp256r1", "org.hyperledger.besu.secp256r1") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("com.goterl:resource-loader", "resource.loader") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }

    module("com.goterl:lazysodium-java", "lazysodium.java") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("tech.pegasys:jc-kzg-4844", "tech.pegasys.jckzg4844") {
        exportAllPackages()
        // no dependencies
    }
    module("net.java.dev.jna:jna", "com.sun.jna") {
        exportAllPackages()
        // no dependencies
    }
    module("org.eclipse.collections:eclipse-collections-api", "org.eclipse.collections.api") {
        exportAllPackages()
        // no dependencies
    }
    module("org.eclipse.collections:eclipse-collections", "org.eclipse.collections.impl") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.prometheus:simpleclient", "io.prometheus.simpleclient") {
        exportAllPackages()
        // no dependencies
    }
    module("io.prometheus:simpleclient_common", "io.prometheus.simpleclient_common") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.prometheus:simpleclient_httpserver", "io.prometheus.simpleclient.httpserver") {
        exportAllPackages()
        requireAllDefinedDependencies()
        requires("jdk.httpserver")
    }

    // Need to use Jar file names here as there is currently no other way to address Jar with
    // classifier directly for patching
    module(
        "netty-transport-native-epoll-4.1.87.Final-linux-x86_64.jar",
        "io.netty.transport.epoll.linux.x86_64"
    )
    module(
        "netty-transport-native-epoll-4.1.87.Final-linux-aarch_64.jar",
        "io.netty.transport.epoll.linux.aarch_64"
    )

    knownModule("com.github.ben-manes.caffeine:caffeine", "com.github.benmanes.caffeine")
    knownModule("com.google.code.gson:gson", "com.google.gson")
    knownModule("io.helidon.grpc:io.grpc", "io.grpc")
    knownModule("org.apache.logging.log4j:log4j-api", "org.apache.logging.log4j")
    knownModule("org.apache.logging.log4j:log4j-slf4j2-impl", "org.apache.logging.log4j.slf4j")
    knownModule("org.bouncycastle:bcpkix-jdk18on", "org.bouncycastle.pkix")
    knownModule("org.bouncycastle:bcprov-jdk18on", "org.bouncycastle.provider")
    knownModule("org.bouncycastle:bcutil-jdk18on", "org.bouncycastle.util")
    knownModule("org.jetbrains.kotlin:kotlin-stdlib-jdk8", "kotlin.stdlib.jdk8")
    knownModule("org.slf4j:slf4j-api", "org.slf4j")
    knownModule("jakarta.inject:jakarta.inject-api", "jakarta.inject")

    // Annotation processing only
    automaticModule("com.google.dagger:dagger-compiler", "dagger.compiler")
    automaticModule("com.google.dagger:dagger-producers", "dagger.producers")
    automaticModule("com.google.dagger:dagger-spi", "dagger.spi")
    automaticModule(
        "com.google.devtools.ksp:symbol-processing-api",
        "com.google.devtools.ksp.symbolprocessingapi"
    )
    automaticModule("com.google.errorprone:javac-shaded", "com.google.errorprone.javac.shaded")
    automaticModule("com.google.googlejavaformat:google-java-format", "com.google.googlejavaformat")
    automaticModule("com.squareup:javapoet", "com.squareup.javapoet")
    automaticModule("net.ltgt.gradle.incap:incap", "net.ltgt.gradle.incap")
    automaticModule("org.jetbrains.kotlinx:kotlinx-metadata-jvm", "kotlinx.metadata.jvm")

    // Testing only
    automaticModule("com.google.jimfs:jimfs", "com.google.jimfs")
    automaticModule("org.awaitility:awaitility", "awaitility")
    automaticModule("org.hamcrest:hamcrest", "org.hamcrest")
    automaticModule("org.hamcrest:hamcrest-core", "org.hamcrest.core")
    automaticModule("org.mockito:mockito-inline", "org.mockito.inline")
    automaticModule("uk.org.webcompere:system-stubs-core", "uk.org.webcompere.systemstubs.core")
    automaticModule(
        "uk.org.webcompere:system-stubs-jupiter",
        "uk.org.webcompere.systemstubs.jupiter"
    )
    automaticModule("com.google.protobuf:protobuf-java-util", "com.google.protobuf.util")

    // JMH only
    automaticModule("net.sf.jopt-simple:jopt-simple", "jopt.simple")
    automaticModule("org.openjdk.jmh:jmh-core", "jmh.core")
    automaticModule("org.openjdk.jmh:jmh-generator-asm", "jmh.generator.asm")
    automaticModule("org.openjdk.jmh:jmh-generator-bytecode", "jmh.generator.bytecode")
    automaticModule("org.openjdk.jmh:jmh-generator-reflection", "jmh.generator.reflection")

    // Test clients only
    automaticModule("com.github.docker-java:docker-java-api", "com.github.docker.java.api")
    automaticModule(
        "com.github.docker-java:docker-java-transport",
        "com.github.docker.java.transport"
    )
    automaticModule(
        "com.github.docker-java:docker-java-transport-zerodep",
        "com.github.docker.transport.zerodep"
    )
    automaticModule("org.rnorth.duct-tape:duct-tape", "org.rnorth.ducttape")
    automaticModule("org.testcontainers:junit-jupiter", "org.testcontainers.junit.jupiter")
    automaticModule("org.testcontainers:testcontainers", "org.testcontainers")
}
