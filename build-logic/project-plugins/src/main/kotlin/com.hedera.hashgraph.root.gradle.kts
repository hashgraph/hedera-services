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

import Utils.Companion.versionTxt
import net.swiftzer.semver.SemVer

plugins {
    id("com.hedera.hashgraph.lifecycle")
    id("com.hedera.hashgraph.repositories")
    id("com.hedera.hashgraph.nexus-publish")
    id("com.hedera.hashgraph.aggregate-reports")
    id("com.hedera.hashgraph.spotless-conventions")
    id("com.hedera.hashgraph.spotless-kotlin-conventions")
    id("com.hedera.hashgraph.dependency-analysis")
}

spotless { kotlinGradle { target("build-logic/**/*.gradle.kts") } }

val productVersion = layout.projectDirectory.versionTxt().asFile.readText().trim()

tasks.register("githubVersionSummary") {
    group = "github"

    inputs.property("version", productVersion)
    outputs.file(
        providers
            .environmentVariable("GITHUB_STEP_SUMMARY")
            .orElse(
                provider {
                    throw IllegalArgumentException(
                        "This task may only be run in a Github Actions CI environment! " +
                            "Unable to locate the GITHUB_STEP_SUMMARY environment variable."
                    )
                }
            )
    )

    doLast {
        Utils.generateProjectVersionReport(
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

    inputs.property(
        "newVersion",
        providers
            .gradleProperty("newVersion")
            .orElse(
                provider {
                    throw IllegalArgumentException(
                        "No newVersion property provided! " +
                            "Please add the parameter -PnewVersion=<version> when running this task."
                    )
                }
            )
    )
    outputs.file(layout.projectDirectory.versionTxt())

    doLast {
        val newVer = SemVer.parse(inputs.properties["newVersion"] as String)
        outputs.files.singleFile.writeText(newVer.toString())
    }
}
