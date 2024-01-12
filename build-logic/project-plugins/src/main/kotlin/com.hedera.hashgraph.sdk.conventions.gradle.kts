/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.java")
}

group = "com.swirlds"

tasks.checkModuleInfo { moduleNamePrefix = "com.swirlds" }

javaModuleDependencies { versionsFromConsistentResolution(":swirlds-platform-core") }

configurations.getByName("mainRuntimeClasspath") {
    extendsFrom(configurations.getByName("internal"))
}

// !!! Remove the following once 'test' tasks are allowed to run in parallel ===
val allPlatformSdkProjects =
    rootProject.subprojects
        .filter { it.projectDir.absolutePath.contains("/platform-sdk/") }
        .map { it.name }
        .filter {
            it !in
                listOf(
                    "swirlds",
                    "swirlds-benchmarks",
                    "swirlds-platform"
                ) // these are application/benchmark projects
        }
        .sorted()
val allHederaNodeCheckTasks =
    rootProject.subprojects
        .filter { it.projectDir.absolutePath.contains("/hedera-node/") }
        .map { "${it.path}:check" }
val myIndex = allPlatformSdkProjects.indexOf(name)

if (myIndex > 0) {
    val predecessorProject = allPlatformSdkProjects[myIndex - 1]
    tasks.test {
        mustRunAfter(":$predecessorProject:test")
        mustRunAfter(":$predecessorProject:hammerTest")
        mustRunAfter(allHederaNodeCheckTasks)
    }
    tasks.named("hammerTest") {
        mustRunAfter(tasks.test)
        mustRunAfter(":$predecessorProject:hammerTest")
        mustRunAfter(allHederaNodeCheckTasks)
    }
}
