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

// All below configuration should eventually be removed once all 'sdk' tests in 'src/test'
// are able to run in parallel without restrictions.
tasks.test {
    options {
        this as JUnitPlatformOptions
        excludeTags("TIMING_SENSITIVE")
    }
}

val timingSensitive =
    tasks.register<Test>("timingSensitive") {
        group = "build"
        description = "Runs the timing sensitive tests of test suite."

        // Separate target (task) for timingSensitive tests.
        // Tests should eventually be fixed or moved to 'hammer'.
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath

        usesService(
            gradle.sharedServices.registerIfAbsent(
                "lock",
                com.hedera.hashgraph.gradlebuild.services.TaskLockService::class
            ) {
                maxParallelUsages = 1
            }
        )
        mustRunAfter(
            rootProject.subprojects
                .filter { File(it.projectDir, "src/test").exists() }
                .map { "${it.path}:test" }
        )

        useJUnitPlatform { includeTags("TIMING_SENSITIVE") }
        maxHeapSize = "4g"
    }

tasks.check { dependsOn(timingSensitive) }
