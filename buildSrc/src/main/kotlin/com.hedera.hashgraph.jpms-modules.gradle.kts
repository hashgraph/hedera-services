/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement yaou entered into
 * with Hedera Hashgraph.
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import com.hedera.hashgraph.gradlebuild.rules.IoGrpcDependencyMetadataRule
import com.hedera.hashgraph.gradlebuild.rules.IoGrpcMetadataRule
import com.hedera.hashgraph.gradlebuild.rules.IoNettyNativeEpollMetadataRule
import com.hedera.hashgraph.gradlebuild.rules.JavaxAnnotationMetadataRule

plugins {
    id("java")
    id("org.gradlex.java-ecosystem-capabilities")
    id("org.gradlex.extra-java-module-info")
    id("org.gradlex.java-module-dependencies")
}

javaModuleDependencies {
    versionsFromConsistentResolution(":hedera-node:node-app")

    moduleNameToGA.put("com.hedera.hashgraph.protobuf.java.api", "com.hedera.hashgraph:hedera-protobuf-java-api")
    moduleNameToGA.put("com.hedera.pbj.runtime", "com.hedera.pbj:pbj-runtime")
    moduleNameToGA.put("com.swirlds.base", "com.swirlds:swirlds-base")
    moduleNameToGA.put("com.swirlds.cli", "com.swirlds:swirlds-cli")
    moduleNameToGA.put("com.swirlds.common.test", "com.swirlds:swirlds-common-test")
    moduleNameToGA.put("com.swirlds.config", "com.swirlds:swirlds-config-api")
    moduleNameToGA.put("com.swirlds.config.impl", "com.swirlds:swirlds-config-impl")
    moduleNameToGA.put("com.swirlds.test.framework", "com.swirlds:swirlds-test-framework")
    moduleNameToGA.put("hamcrest.core", "org.hamcrest:hamcrest-core")
    moduleNameToGA.put("io.grpc", "io.helidon.grpc:io.grpc")
    moduleNameToGA.put("io.helidon.webserver.http2", "io.helidon.webserver:helidon-webserver-http2")
    moduleNameToGA.put("io.netty.codec.http", "io.netty:netty-codec-http")
    moduleNameToGA.put("io.netty.codec.http2", "io.netty:netty-codec-http2")
    moduleNameToGA.put("io.netty.codec.socks", "io.netty:netty-codec-socks")
    moduleNameToGA.put("io.netty.handler.proxy", "io.netty:netty-handler-proxy")
    moduleNameToGA.put("org.apache.logging.log4j.slf4j", "org.apache.logging.log4j:log4j-slf4j-impl")
    moduleNameToGA.put("org.bouncycastle.util", "org.bouncycastle:bcutil-jdk15on")
    moduleNameToGA.put("org.eclipse.collections.api", "org.eclipse.collections:eclipse-collections-api")
    moduleNameToGA.put("org.eclipse.collections.impl", "org.eclipse.collections:eclipse-collections")
    moduleNameToGA.put("org.hamcrest", "org.hamcrest:hamcrest")
    moduleNameToGA.put("org.hyperledger.besu.datatypes", "org.hyperledger.besu:besu-datatypes")
    moduleNameToGA.put("org.hyperledger.besu.evm", "org.hyperledger.besu:evm")
    moduleNameToGA.put("org.hyperledger.besu.internal.rlp", "org.hyperledger.besu.internal:rlp")
    moduleNameToGA.put("org.hyperledger.besu.plugin.api", "org.hyperledger.besu:plugin-api")
    moduleNameToGA.put("org.mockito.junit.jupiter", "org.mockito:mockito-junit-jupiter")
    moduleNameToGA.put("org.objenesis", "org.objenesis:objenesis")
    moduleNameToGA.put("org.antlr.antlr4.runtime", "org.antlr:antlr4-runtime")
}

dependencies.components {
    withModule<IoGrpcMetadataRule>("io.helidon.grpc:io.grpc")

    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-netty")
    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-protobuf")
    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-protobuf-lite")
    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-services")
    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-stub")
    withModule<IoGrpcDependencyMetadataRule>("io.grpc:grpc-testing")

    withModule<IoNettyNativeEpollMetadataRule>("io.netty:netty-transport-native-epoll")

    withModule<JavaxAnnotationMetadataRule>("com.google.code.findbugs:jsr305")
}

extraJavaModuleInfo {
    module("io.grpc:grpc-netty", "grpc.netty") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("io.grpc:grpc-stub", "grpc.stub") {
        exportAllPackages()
        requireAllDefinedDependencies()
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
    }
    module("com.google.code.findbugs:jsr305", "java.annotation") {
        mergeJar("javax.annotation:javax.annotation-api")
        exports("javax.annotation")
        exports("javax.annotation.concurrent")
        exports("javax.annotation.meta")
    }
    module("com.github.spotbugs:spotbugs-annotations", "com.github.spotbugs.annotations") {
        exportAllPackages()
        requires("java.annotation")
    }
    module("com.google.protobuf:protobuf-java", "com.google.protobuf") {
        exportAllPackages()
        requireAllDefinedDependencies()
        requires("java.logging")
    }
    module("com.google.protobuf:protobuf-java-util", "com.google.protobuf.util") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("com.google.errorprone:error_prone_annotations", "com.google.errorprone.annotations") {
        exportAllPackages()
        // no dependencies
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
    module("com.google.j2objc:j2objc-annotations", "com.google.j2objc.annotations") {
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
    module("io.perfmark:perfmark-api", "io.perfmark.perfmark.api") {
        exportAllPackages()
        requireAllDefinedDependencies()
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
    module("com.offbynull.portmapper:portmapper", "portmapper") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.jetbrains:annotations", "org.jetbrains.annotations") {
        exportAllPackages()
        // no dependencies
    }
    module("org.apache.tuweni:tuweni-units", "tuweni.units") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.apache.tuweni:tuweni-bytes", "tuweni.bytes") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("net.i2p.crypto:eddsa", "net.i2p.crypto.eddsa") {
        exportAllPackages()
        // no dependencies
    }
    module("io.netty:netty-transport-native-epoll", "io.netty.transport.epoll") {
        exportAllPackages()
        // no dependencies
    }

    knownModule("com.github.ben-manes.caffeine:caffeine", "com.github.benmanes.caffeine")
    knownModule("com.google.code.gson:gson", "com.google.gson")
    knownModule("com.swirlds:swirlds-common", "com.swirlds.common")
    knownModule("com.swirlds:swirlds-fchashmap", "com.swirlds.fchashmap")
    knownModule("com.swirlds:swirlds-fcqueue", "com.swirlds.fcqueue")
    knownModule("com.swirlds:swirlds-jasperdb", "com.swirlds.jasperdb")
    knownModule("com.swirlds:swirlds-merkle", "com.swirlds.merkle")
    knownModule("com.swirlds:swirlds-platform-core", "com.swirlds.platform.core")
    knownModule("com.swirlds:swirlds-virtualmap", "com.swirlds.virtualmap")
    knownModule("io.github.classgraph:classgraph", "io.github.classgraph")
    knownModule("io.helidon.grpc:helidon-grpc-server", "io.helidon.grpc.server")
    knownModule("io.helidon.grpc:io.grpc", "io.grpc")
    knownModule("io.netty:netty-codec-http2", "io.netty.codec.http2")
    knownModule("io.netty:netty-handler-proxy", "io.netty.handler.proxy")
    knownModule("io.netty:netty-transport-native-unix-common", "io.netty.transport.unix.common")
    knownModule("junit:junit", "junit")
    knownModule("org.apache.logging.log4j:log4j-api", "org.apache.logging.log4j")
    knownModule("org.apache.logging.log4j:log4j-core", "org.apache.logging.log4j.core")
    knownModule("org.apache.logging.log4j:log4j-slf4j", "org.apache.logging.log4j.slf4j")
    knownModule("org.apache.logging.log4j:log4j-jul", "org.apache.logging.log4j.jul")
    knownModule("org.jetbrains.kotlin:kotlin-stdlib-jdk8", "kotlin.stdlib.jdk8")
    knownModule("org.slf4j:slf4j-api", "org.slf4j")

    // Kotlin has to be automatic modules because of split package mess
    automaticModule("org.jetbrains.kotlin:kotlin-stdlib-common", "kotlin.stdlib.common")
    automaticModule("org.jetbrains.kotlinx:kotlinx-metadata-jvm", "kotlinx.metadata.jvm")

    // These have to be automatic modules as they can not be re-jared because they contain native libraries.
    automaticModule("com.goterl:lazysodium-java", "lazysodium.java")
    automaticModule("com.goterl:resource-loader", "resource.loader")
    automaticModule("org.hyperledger.besu:secp256k1", "org.hyperledger.besu.secp256k1")
    automaticModule("org.hyperledger.besu.internal:crypto", "org.hyperledger.besu.crypto")
    automaticModule("tech.pegasys:jc-kzg-4844", "tech.pegasys.jckzg4844")

    automaticModule("org.hyperledger.besu.internal:util", "org.hyperledger.besu.util")
    automaticModule("org.hyperledger.besu:bls12-381", "org.hyperledger.besu.bls12.for381")
    automaticModule("org.hyperledger.besu:secp256r1", "org.hyperledger.besu.secp256r1")
    automaticModule("org.hyperledger.besu:blake2bf", "org.hyperledger.besu.blake2bf")
    automaticModule("org.hyperledger.besu:blake2bf", "org.hyperledger.besu.blake2bf")
    automaticModule("net.java.dev.jna:jna", "com.sun.jna")
    automaticModule("org.bouncycastle:bcprov-jdk15on", "org.bouncycastle.provider")
    automaticModule("org.bouncycastle:bcpkix-jdk15on", "org.bouncycastle.pkix")

    // automatic modules to make it build, might be able to move them full modules
    automaticModule("com.google.android:annotations", "com.google.android.annotations")
    automaticModule("org.codehaus.mojo:animal-sniffer-annotations", "org.codehaus.mojo.animalsniffer.annotations")
    automaticModule("org.eclipse.microprofile.health:microprofile-health-api", "microprofile.health.api")
    automaticModule("org.openjfx:javafx-base", "javafx.base")

    // Compile Time Modules
    automaticModule("net.ltgt.gradle.incap:incap", "net.ltgt.gradle.incap")
    automaticModule("com.google.dagger:dagger-compiler", "dagger.compiler")
    automaticModule("com.google.dagger:dagger-spi", "dagger.spi")
    automaticModule("com.google.dagger:dagger-producers", "dagger.producers")
    automaticModule("com.google.errorprone:javac-shaded", "com.google.errorprone.javac.shaded")
    automaticModule("com.google.googlejavaformat:google-java-format", "com.google.googlejavaformat")
    automaticModule("com.google.devtools.ksp:symbol-processing-api", "com.google.devtools.ksp.symbolprocessingapi")
    automaticModule("org.hyperledger.besu:arithmetic", "org.hyperledger.besu.arithmetic")
    automaticModule("com.squareup:javapoet", "com.squareup.javapoet")
    automaticModule("org.checkerframework:checker-qual", "org.checkerframework.checker.qual")
    automaticModule("io.perfmark:perfmark-api", "perfmark.api")
    automaticModule("org.rnorth.duct-tape:duct-tape", "org.rnorth.ducttape")
    automaticModule("io.opencensus:opencensus-api", "io.opencensus.api")
    automaticModule("org.hyperledger.besu.internal:util", "org.hyperledger.besu.internal.util")
    automaticModule("org.testcontainers:junit-jupiter", "org.testcontainers.junit.jupiter")

    // Automatic modules for PBJ dependencies
    automaticModule("org.antlr:antlr4", "org.antlr.antlr4")
    automaticModule("org.antlr:antlr-runtime", "org.antlr.antlr.runtime")
    automaticModule("org.antlr:ST4", "org.antlr.ST4")
    automaticModule("org.abego.treelayout:org.abego.treelayout.core", "org.abego.treelayout.core")

    // Test Related Modules
    automaticModule("org.mockito:mockito-inline", "org.mockito.inline")
    automaticModule("com.github.docker-java:docker-java-transport", "com.github.docker.java.transport")
    automaticModule("com.github.docker-java:docker-java-transport-zerodep", "com.github.docker.transport.zerodep")
    automaticModule("com.github.docker-java:docker-java-api", "com.github.docker.java.api")
    automaticModule("hamcrest-core-1.3.jar", "hamcrest.core")
    automaticModule("org.awaitility:awaitility", "awaitility")
    automaticModule("org.testcontainers:testcontainers", "org.testcontainers")
    automaticModule("org.testcontainers:junit-jupiter", "org.testcontainers.junit.jupiter")
    automaticModule("io.prometheus:simpleclient", "io.prometheus.simpleclient")
    automaticModule("io.prometheus:simpleclient_common", "io.prometheus.simpleclient_common")
    automaticModule("io.prometheus:simpleclient_httpserver", "io.prometheus.simpleclient.httpserver")
    automaticModule("org.openjdk.jmh:jmh-core", "jmh.core")
    automaticModule("org.openjdk.jmh:jmh-generator-asm", "jmh.generator.asm")
    automaticModule("org.openjdk.jmh:jmh-generator-bytecode", "jmh.generator.bytecode")
    automaticModule("org.openjdk.jmh:jmh-generator-reflection", "jmh.generator.reflection")
    automaticModule("net.sf.jopt-simple:jopt-simple", "jopt.simple")

    automaticModule("uk.org.webcompere:system-stubs-jupiter", "uk.org.webcompere.systemstubs.jupiter")
    automaticModule("uk.org.webcompere:system-stubs-core", "uk.org.webcompere.systemstubs.core")
}
