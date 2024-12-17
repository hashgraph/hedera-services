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
    id("com.hedera.gradle.feature.benchmark")
}

// Remove the following line to enable all 'javac' lint checks that we have turned on by default
// and then fix the reported issues.
tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-Xlint:-static") }

jmhModuleInfo {
    requires("com.hedera.pbj.runtime")
    requires("com.swirlds.base")
    requires("com.swirlds.common")
    requires("com.swirlds.config.api")
    requires("com.swirlds.metrics.api")
    requires("com.swirlds.config.extensions")
    requires("com.swirlds.fchashmap")
    requires("com.swirlds.merkledb")
    requires("com.swirlds.virtualmap")
    requires("com.swirlds.platform.core")
    requires("jmh.core")
    requires("org.apache.logging.log4j")
    requiresStatic("com.github.spotbugs.annotations")
    runtimeOnly("com.swirlds.config.impl")
}

jmh {
    jvmArgs.set(listOf("-Xmx8g"))
    includes.set(listOf("transfer"))
    benchmarkParameters.put("numFiles", listProperty("10"))
    benchmarkParameters.put("keySize", listProperty("16"))
    benchmarkParameters.put("recordSize", listProperty("128"))
}

fun listProperty(value: String) = objects.listProperty<String>().value(listOf(value))

tasks.register<JMHTask>("jmhReconnect") {
    includes.set(listOf("Reconnect.*"))
    jvmArgs.set(
        listOf(
            "-Xmx16g",
            "-Xms16g",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseZGC",
            "-XX:MaxDirectMemorySize=48g"
        )
    )

    resultsFile.convention(layout.buildDirectory.file("results/jmh/results-reconnect.txt"))

    benchmarkParameters.put("numRecords", listProperty("1000"))
    benchmarkParameters.put("numFiles", listProperty("100"))
    benchmarkParameters.put("delayStorageMicroseconds", listProperty("100"))
    benchmarkParameters.put("delayNetworkMicroseconds", listProperty("50"))
    benchmarkParameters.put("teacherAddProbability", listProperty("0.01"))
    benchmarkParameters.put("teacherRemoveProbability", listProperty("0.01"))
    benchmarkParameters.put("teacherModifyProbability", listProperty("0.01"))
}
