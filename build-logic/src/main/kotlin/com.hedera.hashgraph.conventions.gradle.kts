/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

group = "com.hedera.hashgraph"

javaModuleDependencies {
    versionsFromConsistentResolution(":app")
}
configurations.getByName("mainRuntimeClasspath") {
    extendsFrom(configurations.getByName("internal"))
}

testing {
    @Suppress("UnstableApiUsage")
    suites {
        // Add the EET task for executing end-to-end tests
        register<JvmTestSuite>("eet") {
            testType.set("end-to-end-test")

            // "shouldRunAfter" will only make sure if both test and eet are run concurrently,
            // that "test" completes first. If you run "eet" directly, it doesn't force "test" to run.
            targets.all {
                testTask {
                    shouldRunAfter(tasks.test)
                }
            }
        }

        // Add the X-test task for executing "cross-service" tests
        register<JvmTestSuite>("xtest") {
            testType.set("cross-service-test")

            // "shouldRunAfter" will only make sure if both test and xtest are run concurrently,
            // that "test" completes first. If you run "xtest" directly, it doesn't force "test" to run.
            targets.all {
                testTask {
                    shouldRunAfter(tasks.test)
                }
            }
        }
    }
}
