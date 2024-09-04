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

import com.hedera.gradle.services.TaskLockService

plugins { id("java") }

// Test functionally correct behavior under stress/loads with many repeated iterations.
@Suppress("UnstableApiUsage")
testing.suites {
    register<JvmTestSuite>("hammer") {
        testType = "hammer"
        targets.all {
            testTask {
                group = "build"
                usesService(
                    gradle.sharedServices.registerIfAbsent("lock", TaskLockService::class) {
                        maxParallelUsages = 1
                    }
                )
                maxHeapSize = "8g"
            }
        }
    }
}

tasks.assemble { dependsOn(tasks.named("hammerClasses")) }
