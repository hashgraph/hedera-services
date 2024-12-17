/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.gradle.utils.Utils.generateProjectVersionReport
import com.hedera.gradle.utils.Utils.versionTxt
import net.swiftzer.semver.SemVer

plugins {
    id("com.hedera.gradle.lifecycle")
    id("com.hedera.gradle.repositories")
    id("com.hedera.gradle.nexus-publish")
    id("com.hedera.gradle.spotless-kotlin")
    id("com.hedera.gradle.spotless-markdown")
}

spotless {
    kotlinGradle { target("gradle/plugins/**/*.gradle.kts") }
    kotlin {
        // For the Kotlin classes (*.kt files)
        ktfmt().kotlinlangStyle()
        target("gradle/plugins/**/*.kt")
    }
}

val productVersion = layout.projectDirectory.versionTxt().asFile.readText().trim()

tasks.register("githubVersionSummary") {
    group = "github"

    inputs.property("version", productVersion)

    if (!providers.environmentVariable("GITHUB_STEP_SUMMARY").isPresent) {
        // Do not throw an exception if running the `gradlew tasks` task
        if (project.gradle.startParameter.taskNames.contains("githubVersionSummary")) {
            throw IllegalArgumentException(
                "This task may only be run in a Github Actions CI environment! " +
                    "Unable to locate the GITHUB_STEP_SUMMARY environment variable."
            )
        }
    }
    outputs.file(providers.environmentVariable("GITHUB_STEP_SUMMARY"))

    doLast {
        generateProjectVersionReport(
            inputs.properties["version"] as String,
            outputs.files.singleFile.outputStream().buffered()
        )
    }
}

tasks.register("showVersion") {
    group = "versioning"

    inputs.property("version", productVersion)

    doLast { println(inputs.properties["version"]) }
}

tasks.register("versionAsPrefixedCommit") {
    group = "versioning"

    @Suppress("UnstableApiUsage")
    inputs.property(
        "commit",
        providers
            .exec { commandLine("git", "rev-parse", "HEAD") }
            .standardOutput
            .asText
            .map { it.trim().substring(0, 7) }
    )
    inputs.property("commitPrefix", providers.gradleProperty("commitPrefix").orElse("adhoc"))
    inputs.property("version", productVersion)
    outputs.file(layout.projectDirectory.versionTxt())

    doLast {
        val newPrerel =
            inputs.properties["commitPrefix"].toString() +
                ".x" +
                inputs.properties["commit"].toString().take(8)
        val currVer = SemVer.parse(inputs.properties["version"] as String)
        val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, newPrerel)
        outputs.files.singleFile.writeText(newVer.toString())
    }
}

tasks.register("versionAsSnapshot") {
    group = "versioning"

    inputs.property("version", productVersion)
    outputs.file(layout.projectDirectory.versionTxt())

    doLast {
        val currVer = SemVer.parse(inputs.properties["version"] as String)
        val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, "SNAPSHOT")

        outputs.files.singleFile.writeText(newVer.toString())
    }
}

tasks.register("versionAsSpecified") {
    group = "versioning"

    inputs.property("newVersion", providers.gradleProperty("newVersion").orNull)

    if (inputs.properties["newVersion"] == null) {
        // Do not throw an exception if running the `gradlew tasks` task
        if (project.gradle.startParameter.taskNames.contains("versionAsSpecified")) {
            throw IllegalArgumentException(
                "No newVersion property provided! " +
                    "Please add the parameter -PnewVersion=<version> when running this task."
            )
        }
    }
    outputs.file(layout.projectDirectory.versionTxt())

    doLast {
        val newVer = SemVer.parse(inputs.properties["newVersion"] as String)
        outputs.files.singleFile.writeText(newVer.toString())
    }
}
