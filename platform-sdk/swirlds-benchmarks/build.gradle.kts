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
    id("com.hedera.hashgraph.sdk.conventions")
    id("com.hedera.hashgraph.benchmark-conventions")
}

jmhModuleInfo {
    requires("com.hedera.pbj.runtime")
    requires("com.swirlds.base")
    requires("com.swirlds.common")
    requires("com.swirlds.common.test.fixtures")
    requires("com.swirlds.config.api")
    requires("com.swirlds.metrics.api")
    requires("com.swirlds.config.extensions")
    requires("com.swirlds.config.extensions.test.fixtures")
    requires("com.swirlds.fchashmap")
    requires("com.swirlds.merkledb")
    requires("com.swirlds.virtualmap")
    requires("com.swirlds.virtualmap.test.fixtures")
    requires("jmh.core")
    requires("org.apache.logging.log4j")
    requires("org.junit.jupiter.api")
    runtimeOnly("com.swirlds.config.impl")
}

jmh {
    jvmArgs.set(listOf("-Xmx8g"))
    includes.set(listOf("transfer"))
    warmupIterations.set(0)
    iterations.set(1)
    benchmarkParameters.put("numFiles", listProperty("10"))
    benchmarkParameters.put("keySize", listProperty("16"))
    benchmarkParameters.put("recordSize", listProperty("128"))
}

fun listProperty(value: String) = objects.listProperty<String>().value(listOf(value))

tasks.register("jmhReconnect") {
    // Unfortunately, this is the only way to re-configure the jmh extension/plugin today,
    // and one can only ever run a single jmh task in a given build run.
    // So either run `./gradlew jmh` to run the regular JMH task defined above,
    // or run `./gradlew jmhReconnect` to run the override below:
    doFirst {
        jmh.includes.set(listOf("Reconnect.*"))
        jmh.jvmArgs.set(listOf("-Xmx16g"))
        jmh.fork.set(1)
        jmh.warmupIterations.set(2)
        jmh.iterations.set(5)
    }
    finalizedBy("jmh")
}
