/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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
    id("java-library")
    id("jacoco")
    id("com.hedera.gradle.base.jpms-modules")
    id("com.hedera.gradle.base.lifecycle")
    id("com.hedera.gradle.base.version")
    id("com.hedera.gradle.check.dependencies")
    id("com.hedera.gradle.check.javac-lint")
    id("com.hedera.gradle.check.spotless")
    id("com.hedera.gradle.check.spotless-java")
    id("com.hedera.gradle.check.spotless-kotlin")
    id("com.hedera.gradle.feature.git-properties-file")
    id("com.hedera.gradle.feature.java-compile")
    id("com.hedera.gradle.feature.java-doc")
    id("com.hedera.gradle.feature.java-execute")
    id("com.hedera.gradle.feature.test")
    id("com.hedera.gradle.report.test-logger")
}

// Remove the following line to enable all 'javac' lint checks that we have turned on by default
// and then fix the reported issues.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-lossy-conversions")
}

testModuleInfo {
    requires("com.hedera.pbj.runtime")
    requires("com.swirlds.merkle")
    requires("com.swirlds.base.test.fixtures")
    requires("awaitility")
    requires("org.junit.jupiter.params")
    requires("org.mockito.junit.jupiter")
    requires("com.swirlds.metrics.api")
    requires("org.hiero.event.creator")
    requires("org.hiero.event.creator.impl")
    requires("com.swirlds.state.impl")
    requires("org.mockito")
    requires("org.hiero.consensus.gossip")
    requiresStatic("com.github.spotbugs.annotations")
}
