/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import com.adarshr.gradle.testlogger.theme.ThemeType
import com.autonomousapps.AbstractExtension
import com.autonomousapps.DependencyAnalysisSubExtension

plugins {
    id("java")
    id("jacoco")
    id("com.adarshr.test-logger")
    id("com.hedera.hashgraph.jpms-modules")
    id("com.hedera.hashgraph.jpms-module-dependencies")
    id("com.hedera.hashgraph.repositories")
    id("com.hedera.hashgraph.spotless-conventions")
    id("com.hedera.hashgraph.spotless-java-conventions")
    id("com.hedera.hashgraph.spotless-kotlin-conventions")
}

version =
    providers.fileContents(rootProject.layout.projectDirectory.versionTxt()).asText.get().trim()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

configurations.all {
    // In case published versions of a module are also available, always prefer the local one
    resolutionStrategy.preferProjectModules()
}

val internal: Configuration =
    configurations.create("internal") {
        isCanBeConsumed = false
        isCanBeResolved = false
    }

dependencies { "internal"(platform("com.hedera.hashgraph:hedera-dependency-versions")) }

sourceSets.all {
    configurations.getByName(compileClasspathConfigurationName) { extendsFrom(internal) }
    configurations.getByName(runtimeClasspathConfigurationName) { extendsFrom(internal) }

    dependencies {
        // For dependencies of annotation processors use versions from 'hedera-dependency-versions',
        // but not 'runtime' dependencies of the platform (JAVA_API instead of JAVA_RUNTIME).
        annotationProcessorConfigurationName("com.hedera.hashgraph:hedera-dependency-versions") {
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
                attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.REGULAR_PLATFORM))
            }
        }
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    fileMode = 664
    dirMode = 775
}

tasks.jar { exclude("**/classpath.index") }

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).tags(
        "apiNote:a:API Note:",
        "implSpec:a:Implementation Requirements:",
        "implNote:a:Implementation Note:"
    )
}

testing {
    @Suppress("UnstableApiUsage")
    suites {
        // Configure the normal unit test suite to use JUnit Jupiter.
        named<JvmTestSuite>("test") {
            // Enable JUnit as our test engine
            useJUnitJupiter()
            targets.all {
                testTask {
                    options {
                        this as JUnitPlatformOptions
                        excludeTags(
                            "TIME_CONSUMING",
                            "AT_SCALE",
                            "REMOTE_ONLY",
                            "HAMMER",
                            "PERFORMANCE",
                            "PROFILING_ONLY",
                            "INFREQUENT_EXEC_ONLY"
                        )
                    }
                    // Increase the heap size for the unit tests
                    maxHeapSize = "4096m"
                    jvmArgs("-XX:ActiveProcessorCount=16")
                    // Can be useful to set in some cases
                    // testLogging.showStandardStreams = true
                }
            }

            // Add 'targets' (tasks of type Test) for the 'test' test suite: 'hammerTest' and
            // 'performanceTest'.  Gradle's test suite API does not yet allow to add additional
            // targets there (although it's planned).
            // Thus, we use 'tasks.register' and set 'testClassesDirs' and 'sources' to the
            // information we get from the 'source set' (sources) of this 'test suite'.
            tasks.register<Test>("hammerTest") {
                testClassesDirs = sources.output.classesDirs
                classpath = sources.runtimeClasspath

                shouldRunAfter(tasks.test)

                useJUnitPlatform { includeTags("HAMMER") }
                maxHeapSize = "8g"
                jvmArgs("-XX:ActiveProcessorCount=16")
            }

            tasks.register<Test>("performanceTest") {
                testClassesDirs = sources.output.classesDirs
                classpath = sources.runtimeClasspath

                shouldRunAfter(tasks.test)

                useJUnitPlatform {
                    includeTags("TIME_CONSUMING", "AT_SCALE", "REMOTE_ONLY", "PERFORMANCE")
                }

                setForkEvery(1)
                minHeapSize = "2g"
                maxHeapSize = "16g"
                jvmArgs("-XX:ActiveProcessorCount=16", "-XX:+UseZGC")
            }
        }
    }
}

tasks.jacocoTestReport {
    // Configure Jacoco so it outputs XML reports (needed by SonarCloud)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    // Pick up results that have been produced by any of the 'Test' tasks,
    // in this or previous build runs, to combine them in one report
    val allTestTasks = tasks.withType<Test>()
    executionData.from(
        allTestTasks.map { it.extensions.getByType<JacocoTaskExtension>().destinationFile }
    )
    shouldRunAfter(allTestTasks)
}

testlogger {
    theme = ThemeType.MOCHA_PARALLEL
    slowThreshold = 10000
    showPassed = false
    showSkipped = false
    showStandardStreams = true
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = true
}

tasks.assemble {
    // 'assemble' compiles all sources, including all test sources
    dependsOn(tasks.testClasses)
}

tasks.check {
    // 'check' runs all checks, including all test checks
    dependsOn(tasks.jacocoTestReport)
}

// Do not report dependencies from one source set to another as 'required'.
// In particular, in case of test fixtures, the analysis would suggest to
// add as testModuleInfo { require(...) } to the main module. This is
// conceptually wrong, because in whitebox testing the 'main' and 'test'
// module are conceptually considered one module (main module extended with tests)
val dependencyAnalysis = extensions.findByType<AbstractExtension>()

if (dependencyAnalysis is DependencyAnalysisSubExtension) {
    dependencyAnalysis.issues { onAny { exclude(project.path) } }
}
