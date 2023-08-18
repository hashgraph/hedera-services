/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import Utils.Companion.versionTxt
import net.swiftzer.semver.SemVer

plugins {
    id("lifecycle-base")
    id("com.hedera.hashgraph.repositories")
    id("com.hedera.hashgraph.spotless-conventions")
    id("com.hedera.hashgraph.spotless-kotlin-conventions")
    id("lazy.zoo.gradle.git-data-plugin")
}

spotless { kotlinGradle { target("build-logic/**/*.gradle.kts") } }

// This is for configuring the umbrella build - i.e. the root of the repository, which can
// be used to build everything.
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
        dependsOn(
            gradle.includedBuilds
                // Not this build
                .filter { it.projectDir != projectDir }
                // Not the build-logic build
                .filter { it.name != "build-logic" }
                .map { build -> build.task(":${taskName}") }
        )
    }
}

tasks.register("githubVersionSummary") {
    group = "github"
    doLast {
        val ghStepSummaryPath: String =
            providers.environmentVariable("GITHUB_STEP_SUMMARY").orNull
                ?: throw IllegalArgumentException(
                    "This task may only be run in a Github Actions CI environment!" +
                        "Unable to locate the GITHUB_STEP_SUMMARY environment variable."
                )

        Utils.generateProjectVersionReport(
            rootProject,
            File(ghStepSummaryPath).outputStream().buffered()
        )
    }
}

tasks.register("showVersion") {
    group = "versioning"
    doLast { println(project.version) }
}

tasks.register("versionAsPrefixedCommit") {
    group = "versioning"
    doLast {
        gitData.lastCommitHash?.let {
            val prefix = providers.gradleProperty("commitPrefix").getOrElse("adhoc")
            val newPrerel = prefix + ".x" + it.take(8)
            val currVer =
                SemVer.parse(layout.projectDirectory.versionTxt().asFile.readText().trim())
            try {
                val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, newPrerel)
                Utils.updateVersion(rootProject, newVer)
            } catch (e: java.lang.IllegalArgumentException) {
                throw IllegalArgumentException(String.format("%s: %s", e.message, newPrerel), e)
            }
        }
    }
}

tasks.register("versionAsSnapshot") {
    group = "versioning"
    doLast {
        val currVer = SemVer.parse(layout.projectDirectory.versionTxt().asFile.readText().trim())
        val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, "SNAPSHOT")

        Utils.updateVersion(rootProject, newVer)
    }
}

tasks.register("versionAsSpecified") {
    group = "versioning"
    doLast {
        val verStr = providers.gradleProperty("newVersion")

        if (!verStr.isPresent) {
            throw IllegalArgumentException(
                "No newVersion property provided! Please add the parameter -PnewVersion=<version> when running this task."
            )
        }

        val newVer = SemVer.parse(verStr.get())
        Utils.updateVersion(rootProject, newVer)
    }
}
