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

plugins {
    id("com.hedera.hashgraph.conventions")
    id("com.hedera.hashgraph.benchmark-conventions")
    `java-test-fixtures`
}

description = "Hedera Application - MONO Service Implementation"

dependencies {
    javaModuleDependencies {
        annotationProcessor(gav("dagger.compiler"))

        testImplementation(gav("awaitility"))
        testImplementation(gav("com.swirlds.config"))
        testImplementation(gav("io.github.classgraph"))
        testImplementation(gav("net.i2p.crypto.eddsa"))
        testImplementation(gav("org.apache.logging.log4j.core"))
        testImplementation(gav("org.apache.logging.log4j.core"))
        testImplementation(gav("org.assertj.core"))
        testImplementation(gav("org.hamcrest"))
        testImplementation(gav("org.hyperledger.besu.plugin.api"))
        testImplementation(gav("org.junit.jupiter.api"))
        testImplementation(gav("org.junit.jupiter.params"))
        testImplementation(gav("org.junitpioneer"))
        testImplementation(gav("org.mockito"))
        testImplementation(gav("org.mockito.junit.jupiter"))

        jmhImplementation(project(":hedera-node:node-app-hapi-utils"))
        jmhImplementation(project(":hedera-node:node-app-spi"))
        jmhImplementation(gav("com.github.spotbugs.annotations"))
        jmhImplementation(gav("com.google.common"))
        jmhImplementation(gav("com.google.protobuf"))
        jmhImplementation(project(":hedera-node:node-hapi"))
        jmhImplementation(gav("com.swirlds.common"))
        jmhImplementation(gav("com.swirlds.fcqueue"))
        jmhImplementation(gav("com.swirlds.jasperdb"))
        jmhImplementation(gav("com.swirlds.merkle"))
        jmhImplementation(gav("com.swirlds.virtualmap"))
        jmhImplementation(gav("dagger"))
        jmhImplementation(gav("javax.inject"))
        jmhImplementation(gav("jmh.core"))
        jmhImplementation(gav("org.apache.commons.io"))
        jmhImplementation(gav("org.apache.commons.lang3"))
        jmhImplementation(gav("org.hyperledger.besu.datatypes"))
        jmhImplementation(gav("org.hyperledger.besu.evm"))
        jmhImplementation(gav("tuweni.bytes"))
        jmhImplementation(gav("tuweni.units"))
    }
}

val apt = configurations.create("apt")

dependencies { @Suppress("UnstableApiUsage") apt(javaModuleDependencies.gav("dagger.compiler")) }

tasks.withType<JavaCompile> { options.annotationProcessorPath = apt }

val jmhDaggerSources = file("build/generated/sources/annotationProcessor/java/jmh")

java.sourceSets["jmh"].java.srcDir(jmhDaggerSources)

val generatedSources = file("build/generated/sources/annotationProcessor/java/main")

java.sourceSets["main"].java.srcDir(generatedSources)

// Replace variables in semantic-version.properties with build variables
tasks.processResources {
    filesMatching("semantic-version.properties") {
        filter { line: String ->
            if (line.contains("hapi-proto.version")) {
                "hapi.proto.version=" + libs.versions.hapi.proto.get()
            } else if (line.contains("project.version")) {
                "hedera.services.version=" + project.version
            } else {
                line
            }
        }
    }
}
