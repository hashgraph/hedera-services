import net.swiftzer.semver.SemVer
import org.owasp.dependencycheck.reporting.ReportGenerator
import java.io.BufferedOutputStream
import java.time.Duration

/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Hedera Hashgraph.
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

plugins {
    `java-platform`
    id("org.sonarqube")
    id("org.owasp.dependencycheck")
    id("lazy.zoo.gradle.git-data-plugin")
}

// Configure the Sonarqube extension for SonarCloud reporting. These properties should not be changed so no need to
// have them in the gradle.properties defintions.
sonarqube {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "hashgraph")
        property("sonar.projectKey", "com.swirlds:swirlds-platform")
        property("sonar.projectName", "Platform SDK")
        property("sonar.projectVersion", project.version)
//        property(
//            "sonar.projectDescription",
//            ""
//        )
        property("sonar.links.homepage", "https://github.com/hashgraph/hedera-services")
        property("sonar.links.ci", "https://github.com/hashgraph/hedera-services/actions")
        property("sonar.links.issue", "https://github.com/hashgraph/hedera-services/issues")
        property("sonar.links.scm", "https://github.com/hashgraph/hedera-services.git")
    }
}

dependencyCheck {
    autoUpdate = true
    formats = listOf(ReportGenerator.Format.HTML.name, ReportGenerator.Format.XML.name, ReportGenerator.Format.JUNIT.name)
    junitFailOnCVSS = 7.0f
    failBuildOnCVSS = 11.0f
    outputDirectory = File(project.buildDir, "reports/dependency-check").toString()
}

tasks.create("githubVersionSummary") {
    group = "github"
    doLast {
        val ghStepSummaryPath: String = System.getenv("GITHUB_STEP_SUMMARY")
            ?: throw IllegalArgumentException("This task may only be run in a Github Actions CI environment! Unable to locate the GITHUB_STEP_SUMMARY environment variable.")

        val ghStepSummaryFile = File(ghStepSummaryPath)
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
            val currVer = SemVer.parse(project.version.toString())
            try {
                val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, newPrerel)
                Utils.updateVersion(project, newVer)
            } catch (e: java.lang.IllegalArgumentException) {
                throw IllegalArgumentException(String.format("%s: %s", e.message, newPrerel), e)
            }
        }
    }
}

tasks.create("versionAsSnapshot") {
    group = "versioning"
    doLast {
        val currVer = SemVer.parse(project.version.toString())
        val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, "SNAPSHOT")

        Utils.updateVersion(project, newVer)
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
        Utils.updateVersion(project, newVer)
    }
}
