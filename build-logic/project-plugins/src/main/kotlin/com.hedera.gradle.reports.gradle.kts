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

plugins {
    id("jvm-ecosystem")
    id("jacoco-report-aggregation")
    id("com.hedera.hashgraph.repositories")
    id("com.hedera.hashgraph.jpms-modules")
    id("com.hedera.hashgraph.jpms-module-dependencies")
}

dependencies {
    rootProject.subprojects
        .filter { prj -> prj != project }
        .forEach {
            if (it.name == "hedera-dependency-versions") {
                jacocoAggregation(platform(project(it.path)))
            } else {
                jacocoAggregation(project(it.path))
            }
        }
}

// Use Gradle's 'jacoco-report-aggregation' plugin to create an aggregated report independent of the
// platform (Codecov, Codacy, ...) that picks it up later on.
// See:
// https://docs.gradle.org/current/samples/sample_jvm_multi_project_with_code_coverage_standalone.html
reporting {
    reports.create<JacocoCoverageReport>("testCodeCoverageReport") {
        testType = TestSuiteType.UNIT_TEST
    }
}
