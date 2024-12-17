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
    id("com.hedera.gradle.feature.test-fixtures")
    id("com.hedera.gradle.feature.test-timing-sensitive")
}

// Remove the following line to enable all 'javac' lint checks that we have turned on by default
// and then fix the reported issues.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add(
        "-Xlint:-exports,-lossy-conversions,-overloads,-dep-ann,-text-blocks,-varargs"
    )
}

mainModuleInfo {
    annotationProcessor("com.swirlds.config.processor")

    runtimeOnly("resource.loader")
    runtimeOnly("com.sun.jna")
}

testModuleInfo {
    requires("com.swirlds.config.extensions.test.fixtures")
    requires("com.swirlds.base.test.fixtures")
    requires("com.swirlds.config.extensions")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("org.mockito")
    requires("org.mockito.junit.jupiter")
    requiresStatic("com.github.spotbugs.annotations")
}

timingSensitiveModuleInfo {
    requires("com.swirlds.base")
    requires("com.swirlds.base.test.fixtures")
    requires("com.swirlds.common")
    requires("com.swirlds.common.test.fixtures")
    requires("com.swirlds.config.api")
    requires("com.swirlds.config.extensions.test.fixtures")
    requires("com.swirlds.logging")
    requires("com.swirlds.logging.test.fixtures")
    requires("com.swirlds.metrics.api")
    requires("com.swirlds.metrics.impl")
    requires("org.apache.logging.log4j.core")
    requires("org.assertj.core")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("org.mockito")
    requires("org.mockito.junit.jupiter")
}
