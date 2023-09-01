/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.aggregate-reports")
    id("com.hedera.hashgraph.dependency-analysis")
    id("com.hedera.hashgraph.repositories")
    id("com.hedera.hashgraph.spotless-conventions")
    id("com.hedera.hashgraph.spotless-kotlin-conventions")
}

// Lifecycle task configuration:
// Because builds are kept as independent as possible, even if they includeBuild each other,
// you can not do things like './gradlew assemble' to run assemble on all projects.
// You have to explicitly make lifecycle tasks available and link them (via dependsOn) to the
// corresponding lifecycle tasks in the other builds.
// https://docs.gradle.org/current/userguide/structuring_software_products_details.html#using_an_umbrella_build

tasks.register("checkAllModuleInfo")

configureLifecycleTask("clean")

configureLifecycleTask("assemble")

configureLifecycleTask("check")

configureLifecycleTask("build")

configureLifecycleTask("spotlessCheck")

configureLifecycleTask("spotlessApply")

configureLifecycleTask("checkAllModuleInfo")

fun configureLifecycleTask(taskName: String) {
    tasks.named(taskName) {
        // Link the lifecycle tasks in the root project to the corresponding lifecycle tasks in the
        // root projects of all included builds.
        dependsOn(
            gradle.includedBuilds
                // Not this build
                .filter { it.projectDir != projectDir }
                // Not the build-logic build
                .filter { it.name != "build-logic" }
                // Not the depependency-versions build
                .filter { it.name != "hedera-dependency-versions" }
                .map { build -> build.task(":${taskName}") }
        )
        // Link the lifecycle tasks in the root project to the corresponding lifecycle tasks in the
        // subprojects.
        dependsOn(subprojects.map { subproject -> ":${subproject.name}:${taskName}" })
    }
}
