/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
    id("org.hiero.gradle.base.lifecycle")
    id("org.hiero.gradle.report.code-coverage")
}

dependencies {
    implementation(project(":app"))
    implementation(project(":swirlds-platform-base-example"))
    implementation(project(":AddressBookTestingTool"))
    implementation(project(":ConsistencyTestingTool"))
    implementation(project(":ISSTestingTool"))
    implementation(project(":MigrationTestingTool"))
    implementation(project(":PlatformTestingTool"))
    implementation(project(":StatsSigningTestingTool"))
    implementation(project(":StressTestingTool"))
    implementation(project(":test-clients"))
}

tasks.testCodeCoverageReport {
    // Redo the setup done in 'JacocoReportAggregationPlugin', but treat the class file as file tree
    // and filter out selected classes by path.
    val filteredClassFiles = configurations.aggregateCodeCoverageReportResults.get().incoming.artifactView {
        componentFilter { id -> id is ProjectComponentIdentifier }
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
    }.files.asFileTree.filter { file ->
        listOf("test-clients", "testFixtures", "statedumpers", "example-apps").none { file.path.contains(it) }
    }
    classDirectories.setFrom(filteredClassFiles)
}
