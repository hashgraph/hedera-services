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

import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("java-library")
    id("com.hedera.hashgraph.java")
}

group = "com.hedera.hashgraph"

javaModuleDependencies { versionsFromConsistentResolution(":app") }

configurations.getByName("mainRuntimeClasspath") {
    extendsFrom(configurations.getByName("internal"))
}

testing {
    @Suppress("UnstableApiUsage")
    suites {
        // Add the integration test suite
        register<JvmTestSuite>("itest") {
            testType.set(TestSuiteType.INTEGRATION_TEST)

            targets.all {
                testTask {
                    // "shouldRunAfter" will only make sure if both test and itest are run
                    // concurrently, that "test" completes first. If you run "itest"
                    // directly, it doesn't force "test" to run.
                    shouldRunAfter(tasks.test)

                    maxHeapSize = "8g"
                    jvmArgs("-XX:ActiveProcessorCount=6")

                    addTestListener(
                        object : TestListener {
                            override fun beforeSuite(suite: TestDescriptor) {
                                logger.lifecycle(
                                    "=====> Starting Suite: " + suite.displayName + " <====="
                                )
                            }

                            override fun beforeTest(testDescriptor: TestDescriptor) {}

                            override fun afterTest(
                                testDescriptor: TestDescriptor,
                                result: TestResult
                            ) {
                                logger.lifecycle(
                                    SimpleDateFormat.getDateTimeInstance().format(Date()) +
                                        ": " +
                                        testDescriptor.displayName +
                                        " " +
                                        result.resultType.name
                                )
                            }

                            override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
                        }
                    )
                }
            }
        }

        // Add the EET task for executing end-to-end tests
        register<JvmTestSuite>("eet") {
            testType.set("end-to-end-test")

            targets.all {
                testTask {
                    // "shouldRunAfter" will only make sure if both test and eet are run
                    // concurrently, that "test" completes first. If you run "eet"
                    // directly, it doesn't force "test" to run.
                    shouldRunAfter(tasks.test)

                    maxHeapSize = "8g"
                    jvmArgs("-XX:ActiveProcessorCount=6")
                }
            }
        }

        // Add the X-test task for executing "cross-service" tests
        register<JvmTestSuite>("xtest") {
            testType.set("cross-service-test")

            targets.all {
                testTask {
                    // "shouldRunAfter" will only make sure if both test and xtest are run
                    // concurrently, that "test" completes first. If you run "xtest"
                    // directly, it doesn't force "test" to run.
                    shouldRunAfter(tasks.test)

                    maxHeapSize = "8g"
                    jvmArgs("-XX:ActiveProcessorCount=7")
                }
            }
        }
    }
}

tasks.assemble {
    // 'assemble' compiles all sources, including all test sources
    dependsOn(tasks.named("itestClasses"))
    dependsOn(tasks.named("eetClasses"))
    dependsOn(tasks.named("xtestClasses"))
}
