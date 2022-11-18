import gradle.kotlin.dsl.accessors._34a132ac50631db3ea5353237b274f3d.extraJavaModuleInfo

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
    failOnMissingModuleInfo.set(false)
    automaticModule("com.hedera.hashgraph:protobuf-java", "com.hedera.hashgraph.protobuf.java.api")

    automaticModule("com.goterl:lazysodium-java", "lazysodium.java")
    automaticModule("com.goterl:resource-loader", "resource.loader")

    automaticModule("io.grpc:grpc-api", "io.grpc.api")
    automaticModule("io.grpc:grpc-netty", "io.grpc.netty")
    automaticModule("io.grpc:grpc-stub", "grpc.stub")
    automaticModule("io.grpc:grpc-protobuf", "grpc.protobuf")
    automaticModule("io.grpc:grpc-services", "grpc.services")
    automaticModule("io.grpc:grpc-protobuf-lite", "grpc.protobuf.lite")
    automaticModule("io.grpc:grpc-netty", "grpc.netty")

    automaticModule("org.openjdk.jmh:jmh-core", "jmh.core")
    automaticModule("org.openjdk.jmh:jmh-generator-asm", "jmh.generator.asm")
    automaticModule("org.openjdk.jmh:jmh-generator-bytecode", "jmh.generator.bytecode")
    automaticModule("org.openjdk.jmh:jmh-generator-reflection", "jmh.generator.reflection")

    automaticModule("org.jetbrains:annotations", "annotations")

    automaticModule("org.hyperledger.besu:secp256k1", "org.hyperledger.besu.secp256k1")
    automaticModule("org.hyperledger.besu.internal:crypto", "org.hyperledger.besu.crypto")

    automaticModule("javax.annotation:javax.annotation-api", "javax.annotation.api")
    automaticModule("javax.inject:javax.inject", "javax.inject")

    automaticModule("net.sf.jopt-simple:jopt-simple", "jopt.simple")

    automaticModule("org.apache.commons:commons-collections4", "commons.collections4")

    automaticModule("commons-io:commons-io", "org.apache.commons.io")

    automaticModule("com.offbynull.portmapper:portmapper", "portmapper")

    automaticModule("org.openjfx:javafx-base", "javafx.base")

    automaticModule("io.prometheus:simpleclient", "io.prometheus.simpleclient")
    automaticModule("io.prometheus:simpleclient_common", "io.prometheus.simpleclient_common")
    automaticModule("io.prometheus:simpleclient_httpserver", "io.prometheus.simpleclient.httpserver")

    automaticModule("j2objc-annotations-1.3.jar", "j2objc.annotations")
    automaticModule("io.perfmark:perfmark-api", "perfmark.api")

    automaticModule("org.eclipse.microprofile.health:microprofile-health-api", "microprofile.health.api")

    automaticModule("com.google.code.findbugs:jsr305", "jsr305")
    automaticModule("com.google.guava:listenablefuture", "listenablefuture")
    automaticModule("com.google.guava:failureaccess", "failureaccess")
    automaticModule("com.google.auto.value:auto-value-annotations", "auto.value.annotations")

    // Test Related Modules
    automaticModule("com.github.docker-java:docker-java-transport", "com.github.docker.java.transport")
    automaticModule("com.github.docker-java:docker-java-api", "com.github.docker.java.api")
    automaticModule("com.google.truth:truth", "truth")
    automaticModule("hamcrest-core-1.3.jar", "hamcrest.core")
    automaticModule("org.awaitility:awaitility", "awaitility")
    automaticModule("org.testcontainers:testcontainers", "org.testcontainers")
}
