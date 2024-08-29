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

plugins {
    id("com.hedera.gradle.platform")
    id("com.hedera.gradle.platform-publish")
    id("com.hedera.gradle.feature.benchmark")
    id("com.hedera.gradle.feature.test-fixtures")
    id("com.hedera.gradle.feature.test-timing-sensitive")
}

// Remove the following line to enable all 'javac' lint checks that we have turned on by default
// and then fix the reported issues.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-deprecation,-exports,-removal,-varargs")
}

mainModuleInfo { annotationProcessor("com.google.auto.service.processor") }

testModuleInfo {
    requires("org.apache.logging.log4j.core")
    requires("org.apache.commons.lang3")
    requires("com.swirlds.config.extensions.test.fixtures")
    requires("com.swirlds.logging.test.fixtures")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("com.swirlds.base.test.fixtures")
    requires("com.swirlds.common.test.fixtures")
    requires("jakarta.inject")
}

timingSensitiveModuleInfo {
    requires("com.swirlds.base.test.fixtures")
    requires("com.swirlds.config.api")
    requires("com.swirlds.logging.test.fixtures")
    requires("jakarta.inject")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    runtimeOnly("com.swirlds.common.test.fixtures")
}

jmhModuleInfo {
    requires("com.swirlds.logging")
    requires("org.apache.logging.log4j")
    requires("com.swirlds.config.api")
    runtimeOnly("com.swirlds.config.impl")
    requires("com.swirlds.config.extensions")
    runtimeOnly("com.swirlds.logging.log4j.appender")
    requires("org.apache.logging.log4j.core")
    requires("com.github.spotbugs.annotations")
    requires("jmh.core")
}
