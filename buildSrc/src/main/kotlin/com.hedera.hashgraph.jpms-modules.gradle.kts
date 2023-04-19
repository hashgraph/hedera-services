import gradle.kotlin.dsl.accessors._279a83ebe69c565deca49009fdc57437.javaModulesMergeJars

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

plugins {
    id("org.gradlex.extra-java-module-info")
}

extraJavaModuleInfo {
    failOnMissingModuleInfo.set(true)

    // This is a really complex module, it takes all the core io.grpc library jars we depend on and creates a single
    // Java Module out of them. This is done because they have split packages.
    module("io.grpc:grpc-api", "io.grpc") {
        mergeJar("io.grpc:grpc-context")
        mergeJar("io.grpc:grpc-core")
        exports("io.grpc")
        exports("io.grpc.inprocess")
        exports("io.grpc.internal")
        exports("io.grpc.util")
        requiresTransitive("com.google.protobuf")
        requiresTransitive("com.google.common")
    }
    module("io.grpc:grpc-netty", "grpc.netty") {
        exportAllPackages()
        requires("io.grpc")
    }
    module("io.grpc:grpc-stub", "grpc.stub") {
        exportAllPackages()
        requires("io.grpc")
    }
    module("io.grpc:grpc-services", "grpc.services") {
        exportAllPackages()
        requires("io.grpc")
    }
    module("io.grpc:grpc-protobuf", "grpc.protobuf") {
        exportAllPackages()
        requires("io.grpc")
    }
    module("io.grpc:grpc-protobuf-lite", "grpc.protobuf.lite") {
        exportAllPackages()
        requires("io.grpc")
    }
    // This is another complex module as it has two indipendent jar libraries that both contribute class files for the
    // javax.annotation package. To work around it we merge the two jars into one module
    module("com.google.code.findbugs:jsr305", "javax.annotation") {
        mergeJar("javax.annotation:javax.annotation-api")
        exports("javax.annotation")
        exports("javax.annotation.concurrent")
        exports("javax.annotation.meta")
        requireAllDefinedDependencies()
    }
    // Mostly Simple Module Mappings. Some need requireAllDefinedDependencies() and others do not. For each module we
    // let "extra-java-module-info" plugin do automatic exports and then generate a new jar with a generated
    // module-info.java
    module("com.github.spotbugs:spotbugs-annotations", "com.github.spotbugs.annotations") {
        exportAllPackages()
        requires("javax.annotation")
    }
    module("com.google.protobuf:protobuf-java", "com.google.protobuf") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("com.google.protobuf:protobuf-java-util", "com.google.protobuf.util") {
        exportAllPackages()
        requires("com.google.protobuf")
    }
    module("com.google.errorprone:error_prone_annotations", "com.google.errorprone.annotations") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("com.google.guava:guava", "com.google.common") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("com.google.guava:listenablefuture", "com.google.guava.listenablefuture") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("com.google.guava:failureaccess", "com.google.guava.failureaccess") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("com.google.j2objc:j2objc-annotations", "com.google.j2objc.annotations") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("com.google.api.grpc:proto-google-common-protos", "com.google.api.grpc.common") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("com.google.dagger:dagger", "dagger") {
        exportAllPackages()
        requires("javax.inject")
    }
    module("io.perfmark:perfmark-api", "io.perfmark.perfmark-api") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("javax.inject:javax.inject", "javax.inject") {
        exportAllPackages()
    }
    module("org.apache.commons:commons-lang3", "org.apache.commons.lang3") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.apache.commons:commons-codec", "org.apache.commons.codec") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.apache.commons:commons-math3", "org.apache.commons.math3") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.apache.commons:commons-collections4", "org.apache.commons.collections4") {
        exportAllPackages()
    }
    module("com.esaulpaugh:headlong", "headlong") {
        exportAllPackages()
    }
    module("org.connid:framework", "org.connid.framework") {
        exportAllPackages()
    }
    module("org.connid:framework-internal", "org.connid.framework.internal") {
        exportAllPackages()
        requires("org.connid.framework")
    }
    module("com.offbynull.portmapper:portmapper", "portmapper") {
        exportAllPackages()
    }
    module("org.jetbrains.kotlin:kotlin-stdlib-jdk8", "kotlin.stdlib.jdk8") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.jetbrains.kotlin:kotlin-stdlib-common", "kotlin.stdlib.common") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.jetbrains.kotlin:kotlin-stdlib", "kotlin.stdlib") {
        exportAllPackages()
        requireAllDefinedDependencies()
    }
    module("org.jetbrains.kotlinx:kotlinx-metadata-jvm", "kotlinx.metadata.jvm") {
        exportAllPackages()
        requires("kotlin.stdlib")
    }
    module("org.jetbrains:annotations", "org.jetbrains.annotations") {
        exportAllPackages()
        requireAllDefinedDependencies()
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
//        requireAllDefinedDependencies()
    }

    // These are libraries that are ACTUALY! Java Modules :-)
    knownModule("com.github.ben-manes.caffeine:caffeine", "com.github.benmanes.caffeine")
    knownModule("org.bouncycastle:bcprov-jdk15on", "org.bouncycastle.provider")
    knownModule("org.bouncycastle:bcpkix-jdk15on", "org.bouncycastle.pkix")
    knownModule("org.slf4j:slf4j-api", "org.slf4j")
    knownModule("org.apache.logging.log4j:log4j-api", "org.apache.logging.log4j")
    knownModule("org.apache.logging.log4j:log4j-core", "org.apache.logging.log4j.core")
    knownModule("org.apache.logging.log4j:log4j-slf4j", "org.apache.logging.log4j.slf4j")
    knownModule("org.jetbrains.kotlin:kotlin-stdlib-jdk8", "kotlin.stdlib.jdk8")
    knownModule("com.google.code.gson:gson", "com.google.code.gson")
    knownModule("io.github.classgraph:classgraph", "io.github.classgraph")
    knownModule("io.helidon.grpc:helidon-grpc-server", "io.helidon.grpc.server")

    knownModule("com.swirlds:swirlds-common", "com.swirlds.common")
    knownModule("com.swirlds:platform-core", "com.swirlds.platform.core")
    knownModule("com.swirlds:fchashmap", "com.swirlds.fchashmap")
    knownModule("com.swirlds:merkle", "com.swirlds.merkle")
    knownModule("com.swirlds:fcqueue", "com.swirlds.fcqueue")
    knownModule("com.swirlds:jasperdb", "com.swirlds.jasperdb")
    knownModule("com.swirlds:virtualmap", "com.swirlds.virtualmap")

    // These have to be automatic modules as they can not be re-jared because they contain native libraries.
    automaticModule("com.goterl:lazysodium-java", "lazysodium.java")
    automaticModule("com.goterl:resource-loader", "resource.loader")
    automaticModule("org.hyperledger.besu:secp256k1", "org.hyperledger.besu.secp256k1")
    automaticModule("org.hyperledger.besu.internal:crypto", "org.hyperledger.besu.crypto")
    automaticModule("org.hyperledger.besu.internal:util", "org.hyperledger.besu.util")
    automaticModule("org.hyperledger.besu:bls12-381", "org.hyperledger.besu.bls12.for381")
    automaticModule("org.hyperledger.besu:secp256r1", "org.hyperledger.besu.secp256r1")
    automaticModule("org.hyperledger.besu:blake2bf", "org.hyperledger.besu.blake2bf")
    automaticModule("org.hyperledger.besu:blake2bf", "org.hyperledger.besu.blake2bf")
    automaticModule("net.java.dev.jna:jna", "com.sun.jna")
    automaticModule("io.netty:netty-transport-native-epoll", "io.netty.transport.epoll")
    automaticModule("io.netty:netty-transport-native-unix-common", "io.netty.transport.unix.common")
    automaticModule("io.netty:netty-handler", "io.netty.handler")
    automaticModule("io.netty:netty-handler-proxy", "io.netty.handler.proxy")
    automaticModule("io.netty:netty-codec-http2", "io.netty.codec.http2")

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
    automaticModule("com.squareup:javapoet", "com.squareup.javapoet")
    automaticModule("org.checkerframework:checker-qual", "org.checkerframework.checker.qual")
    automaticModule("org.checkerframework:checker-compat-qual", "org.checkerframework.checker.compat.qual")
    automaticModule("io.perfmark:perfmark-api", "perfmark.api")
    automaticModule("org.rnorth.duct-tape:duct-tape", "org.rnorth.ducttape")

    // Test Related Modules
    automaticModule("org.junit:junit-bom","org.junit.junit")
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
}

dependencies.constraints {
    // Here we have to add dependecies for all jar files that are merged into modules above in extraJavaModuleInfo block
    // These would ideally pick up versions numbers from version catalog
    javaModulesMergeJars("io.grpc:grpc-api:1.54.0")
    javaModulesMergeJars("io.grpc:grpc-context:1.54.0")
    javaModulesMergeJars("io.grpc:grpc-core:1.54.0")
    javaModulesMergeJars("javax.annotation:javax.annotation-api:1.3.2")
    javaModulesMergeJars("com.google.code.findbugs:jsr305:3.0.2")
    javaModulesMergeJars("com.google.protobuf:protobuf-java:3.21.12")
}