/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
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
    automaticModule("com.goterl:lazysodium-java", "lazysodium.java")
    automaticModule("com.goterl:resource-loader", "resource.loader")
    automaticModule("org.openjdk.jmh:jmh-core", "jmh.core")
    automaticModule("org.openjdk.jmh:jmh-generator-asm", "jmh.generator.asm")
    automaticModule("org.openjdk.jmh:jmh-generator-bytecode", "jmh.generator.bytecode")
    automaticModule("org.openjdk.jmh:jmh-generator-reflection", "jmh.generator.reflection")
    automaticModule("net.sf.jopt-simple:jopt-simple", "jopt.simple")
    automaticModule("commons-math3-3.2.jar", "commons.math3")
    automaticModule("commons-math3-3.6.1.jar", "commons.math3")

    automaticModule("org.apache.commons:commons-collections4", "commons.collections4")
    automaticModule("commons-io:commons-io", "org.apache.commons.io")
    automaticModule("com.offbynull.portmapper:portmapper", "portmapper")
    automaticModule("org.openjfx:javafx-base", "javafx.base")
    automaticModule("io.prometheus:simpleclient", "io.prometheus.simpleclient")
    automaticModule("io.prometheus:simpleclient_common", "io.prometheus.simpleclient_common")
    automaticModule("io.prometheus:simpleclient_httpserver", "io.prometheus.simpleclient.httpserver")

    automaticModule("hamcrest-core-1.3.jar", "hamcrest.core")
    automaticModule("j2objc-annotations-1.3.jar", "j2objc.annotations")
    automaticModule("com.google.code.findbugs:jsr305", "jsr305")
    automaticModule("com.google.guava:listenablefuture", "listenablefuture")
    automaticModule("com.google.guava:failureaccess", "failureaccess")
    automaticModule("com.google.auto.value:auto-value-annotations", "auto.value.annotations")
    automaticModule("com.google.truth:truth", "truth")
    automaticModule("org.awaitility:awaitility", "awaitility")
}

