// SPDX-License-Identifier: Apache-2.0
plugins {
    id("org.hiero.gradle.base.lifecycle")
    id("org.hiero.gradle.report.code-coverage")
    id("org.hiero.gradle.check.spotless")
    id("org.hiero.gradle.check.spotless-kotlin")
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
    val filteredClassFiles =
        configurations.aggregateCodeCoverageReportResults
            .get()
            .incoming
            .artifactView {
                componentFilter { id -> id is ProjectComponentIdentifier }
                attributes.attribute(
                    LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    objects.named(LibraryElements.CLASSES)
                )
            }
            .files
            .asFileTree
            .filter { file ->
                listOf("test-clients", "testFixtures", "statedumpers", "example-apps").none {
                    file.path.contains(it)
                }
            }
    classDirectories.setFrom(filteredClassFiles)
}
