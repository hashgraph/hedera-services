import net.swiftzer.semver.SemVer
import java.io.BufferedOutputStream

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
    `java-platform`
    id("org.sonarqube")
    id("lazy.zoo.gradle.git-data-plugin")
}

//  Configure the Sonarqube extension for SonarCloud reporting. These properties should not be changed so no need to
// have them in the gradle.properties defintions.
sonarqube {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "hashgraph")
        property("sonar.projectKey", "com.hedera.hashgraph:hedera-services")
        property("sonar.projectName", "Hedera Services")
        property("sonar.projectVersion", project.version)
        property(
            "sonar.projectDescription",
            "Hedera Services (crypto, file, contract, consensus) on the Platform"
        )
        property("sonar.links.homepage", "https://github.com/hashgraph/hedera-services")
        property("sonar.links.ci", "https://github.com/hashgraph/hedera-services/actions")
        property("sonar.links.issue", "https://github.com/hashgraph/hedera-services/issues")
        property("sonar.links.scm", "https://github.com/hashgraph/hedera-services.git")

        property("sonar.coverage.exclusions", "**/test-clients/**,**/hedera-node/src/jmh/**")

        // Ignored to match pom.xml setup
        property("sonar.issue.ignore.multicriteria", "e1,e2")
        property("sonar.issue.ignore.multicriteria.e1.resourceKey", "**/*.java")
        property("sonar.issue.ignore.multicriteria.e1.ruleKey", "java:S125")
        property("sonar.issue.ignore.multicriteria.e2.resourceKey", "**/*.java")
        property("sonar.issue.ignore.multicriteria.e2.ruleKey", "java:S1874")
    }
}

tasks.create("githubVersionSummary") {
    group = "github"
    doLast {
        val ghStepSummaryPath: String? = System.getenv("GITHUB_STEP_SUMMARY")
        if (ghStepSummaryPath == null) {
            throw IllegalArgumentException("This task may only be run in a Github Actions CI environment! Unable to locate the GITHUB_STEP_SUMMARY environment variable.")
        }

        val ghStepSummaryFile: File = File(ghStepSummaryPath)
        Utils.generateProjectVersionReport(rootProject, BufferedOutputStream(ghStepSummaryFile.outputStream()))
    }
}

tasks.create("showVersion") {
    group = "versioning"
    doLast {
        println(project.version)
    }
}

tasks.create("versionAsPrefixedCommit") {
    group = "versioning"
    doLast {
        gitData.lastCommitHash?.let {
            val prefix = findProperty("commitPrefix")?.toString() ?: "adhoc"
            val newPrerel = prefix + ".x" + it.take(8)
            val currVer = SemVer.parse(rootProject.version.toString())
            try {
                val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, newPrerel)
                Utils.updateVersion(rootProject, newVer)
            } catch (e: java.lang.IllegalArgumentException) {
                throw IllegalArgumentException(String.format("%s: %s", e.message, newPrerel), e)
            }
        }
    }
}

tasks.create("versionAsSnapshot") {
    group = "versioning"
    doLast {
        val currVer = SemVer.parse(rootProject.version.toString())
        val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, "SNAPSHOT")

        Utils.updateVersion(rootProject, newVer)
    }
}

tasks.create("versionAsSpecified") {
    group = "versioning"
    doLast {
        val verStr = findProperty("newVersion")?.toString()

        if (verStr == null) {
            throw IllegalArgumentException("No newVersion property provided! Please add the parameter -PnewVersion=<version> when running this task.")
        }

        val newVer = SemVer.parse(verStr)
        Utils.updateVersion(rootProject, newVer)
    }
}
