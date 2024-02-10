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

import Utils.Companion.versionTxt
import org.owasp.dependencycheck.reporting.ReportGenerator

plugins {
    id("org.sonarqube")
    id("org.owasp.dependencycheck")
}

// Configure the Sonarqube extension for SonarCloud reporting.
// These properties should not be changed so no need to
// have them in the gradle.properties definitions.
sonarqube {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "hashgraph")
        property("sonar.projectKey", "com.hedera.hashgraph:hedera-services")
        property("sonar.projectName", "Hedera Services")
        property(
            "sonar.projectDescription",
            "Hedera Services (crypto, file, contract, consensus) on the Platform"
        )
        property(
            "sonar.projectVersion",
            layout.projectDirectory.versionTxt().asFile.readText().trim()
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

dependencyCheck {
    autoUpdate = true
    formats =
        listOf(
            ReportGenerator.Format.HTML.name,
            ReportGenerator.Format.XML.name,
            ReportGenerator.Format.JUNIT.name
        )
    junitFailOnCVSS = 7.0f
    failBuildOnCVSS = 11.0f
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.toString()
}
