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
    id("com.hedera.gradle.platform")
    id("com.hedera.gradle.feature.benchmark")
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
    requires("org.mockito")
    requires("org.hiero.consensus.gossip")
    requiresStatic("com.github.spotbugs.annotations")
}
