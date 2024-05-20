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

import org.owasp.dependencycheck.reporting.ReportGenerator

plugins { id("org.owasp.dependencycheck") }

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
