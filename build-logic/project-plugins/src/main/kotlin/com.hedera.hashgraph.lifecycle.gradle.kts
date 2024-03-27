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
    id("base")
    id("com.diffplug.spotless")
}

// Convenience for local development: when running './gradlew' without any parameters just show the
// tasks from the 'build' group
defaultTasks("tasks")

tasks.named<TaskReportTask>("tasks") {
    if (!isDetail) {
        displayGroup = "build"
    }
}

tasks.register("qualityGate") {
    group = "build"
    description = "Apply spotless rules and run all quality checks."
    dependsOn(tasks.spotlessApply)
    dependsOn(tasks.assemble)
}

tasks.register("releaseMavenCentral")
