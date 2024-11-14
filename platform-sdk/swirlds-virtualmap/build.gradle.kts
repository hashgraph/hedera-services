/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import me.champeau.jmh.JMHTask

plugins {
    id("com.hedera.gradle.platform")
    id("com.hedera.gradle.platform-publish")
    id("com.hedera.gradle.feature.benchmark")
    id("com.hedera.gradle.feature.test-fixtures")
    id("com.hedera.gradle.feature.test-hammer")
    id("com.hedera.gradle.feature.test-timing-sensitive")
}

// Remove the following line to enable all 'javac' lint checks that we have turned on by default
// and then fix the reported issues.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-exports,-lossy-conversions,-synchronization")
}

mainModuleInfo { annotationProcessor("com.swirlds.config.processor") }

jmhModuleInfo {
    requires("com.swirlds.common")
    requires("com.swirlds.common.test.fixtures")
    requires("com.swirlds.config.api")
    requires("com.swirlds.config.extensions.test.fixtures")
    requires("com.swirlds.virtualmap.test.fixtures")
    requires("jmh.core")
    requires("org.junit.jupiter.api")
}

testModuleInfo {
    requires("com.swirlds.common.test.fixtures")
    requires("com.swirlds.virtualmap.test.fixtures")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("org.mockito")
}

timingSensitiveModuleInfo {
    requires("com.swirlds.base")
    requires("com.swirlds.common")
    requires("com.swirlds.common.test.fixtures")
    requires("com.swirlds.config.api")
    requires("com.swirlds.config.extensions")
    requires("com.swirlds.config.extensions.test.fixtures")
    requires("com.swirlds.metrics.api")
    requires("com.swirlds.virtualmap.test.fixtures")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("org.mockito")
}

hammerModuleInfo {
    requires("com.swirlds.common")
    requires("com.swirlds.common.test.fixtures")
    requires("com.swirlds.virtualmap")
    requires("com.swirlds.virtualmap.test.fixtures")
    requires("com.swirlds.config.api")
    requires("org.junit.jupiter.api")
    runtimeOnly("com.swirlds.config.impl")
}

tasks.register<JMHTask>("jmhReconnect") {
    includes.set(listOf("Reconnect.*"))
    jvmArgs.set(listOf("-Xmx16g"))
    fork.set(1)
    warmupIterations.set(2)
    iterations.set(5)

    resultsFile.convention(layout.buildDirectory.file("results/jmh/results-reconnect.txt"))
}
