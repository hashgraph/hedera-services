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

import Utils.Companion.testLogger
import Utils.Companion.versionTxt
import com.adarshr.gradle.testlogger.theme.ThemeType
import com.autonomousapps.AbstractExtension
import com.autonomousapps.DependencyAnalysisSubExtension
import com.hedera.hashgraph.gradlebuild.services.TaskLockService

plugins {
    id("java")
    id("jacoco")
    id("checkstyle")
    id("com.adarshr.test-logger")
    id("com.hedera.hashgraph.lifecycle")
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
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

configurations.all {
    // In case published versions of a module are also available, always prefer the local one
    resolutionStrategy.preferProjectModules()
}

@Suppress("UnstableApiUsage") val internal = configurations.dependencyScope("internal")

javaModuleDependencies { versionsFromConsistentResolution(":app") }

configurations.getByName("mainRuntimeClasspath") { extendsFrom(internal.get()) }

configurations.javaModulesMergeJars { extendsFrom(internal.get()) }

dependencies { "internal"(platform("com.hedera.hashgraph:hedera-dependency-versions")) }

tasks.buildDependents { setGroup(null) }

tasks.buildNeeded { setGroup(null) }

tasks.jar { setGroup(null) }

sourceSets.all {
    // Remove 'classes' tasks from 'build' group to keep it cleaned up
    tasks.named(classesTaskName) { group = null }

    configurations.getByName(compileClasspathConfigurationName) { extendsFrom(internal.get()) }
    configurations.getByName(runtimeClasspathConfigurationName) { extendsFrom(internal.get()) }

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

val writeGitProperties =
    tasks.register<WriteProperties>("writeGitProperties") {
        property("git.build.version", project.version)
        @Suppress("UnstableApiUsage")
        property(
            "git.commit.id",
            providers
                .exec { commandLine("git", "rev-parse", "HEAD") }
                .standardOutput
                .asText
                .map { it.trim() }
        )
        @Suppress("UnstableApiUsage")
        property(
            "git.commit.id.abbrev",
            providers
                .exec { commandLine("git", "rev-parse", "HEAD") }
                .standardOutput
                .asText
                .map { it.trim().substring(0, 7) }
        )

        destinationFile = layout.buildDirectory.file("generated/git/git.properties")
    }

tasks.processResources { from(writeGitProperties) }

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    fileMode = 436 // octal: 0664
    dirMode = 509 // octal: 0775
}

tasks.jar { exclude("**/classpath.index") }

val deactivatedCompileLintOptions =
    listOf(
        // In Gradle, a module does not see the upstream (not-yet-compiled) modules. This could
        // only be solved by calling 'javac' with '--source-module-path' to make other sources
        // known. But this is at odds with how Gradle's incremental compilation calls the
        // compiler for a subset of Java files for each project individually.
        "module", // module not found when doing 'exports to ...'
        "serial", // serializable class ... has no definition of serialVersionUID
        "processing", // No processor claimed any of these annotations: ...
        "try", // auto-closeable resource ignore is never referenced... (AutoClosableLock)
        "missing-explicit-ctor", // class ... declares no explicit constructors

        // Needed because we use deprecation internally and do not fix all uses right away
        "removal",
        "deprecation",

        // The following checks could be activated and fixed:
        "this-escape", // calling public/protected method in constructor
        "overrides", // overrides equals, but neither it ... overrides hashCode method
        "unchecked",
        "rawtypes"
    )

tasks.withType<JavaCompile>().configureEach {
    // Track the full Java version as input (e.g. 17.0.3 vs. 17.0.9).
    // By default, Gradle only tracks the major version as defined in the toolchain (e.g. 17).
    // Since the full version is encoded in 'module-info.class' files, it should be tracked as
    // it otherwise leads to wrong build cache hits.
    inputs.property("fullJavaVersion", providers.systemProperty("java.version"))

    options.encoding = "UTF-8"
    options.compilerArgs.add("-implicit:none")
    options.compilerArgs.add("-Werror")
    options.compilerArgs.add("-Xlint:all,-" + deactivatedCompileLintOptions.joinToString(",-"))

    doLast {
        // Make sure consistent line ending are used in files generated by annotation processors by
        // rewriting generated files.
        // To fix this problem at the root, one of these issues needs to be addressed upstream:
        // - https://github.com/google/auto/issues/1656
        // - https://github.com/gradle/gradle/issues/27385
        if (System.lineSeparator() != "\n") {
            destinationDirectory
                .get()
                .asFileTree
                .filter { it.extension != "class" }
                .forEach {
                    val content = it.readText()
                    val normalizedContent = content.replace(System.lineSeparator(), "\n")
                    if (content != normalizedContent) {
                        it.writeText(normalizedContent)
                    }
                }
        }
    }
}

tasks.withType<Javadoc>().configureEach {
    options {
        this as StandardJavadocDocletOptions
        encoding = "UTF-8"
        tags(
            "apiNote:a:API Note:",
            "implSpec:a:Implementation Requirements:",
            "implNote:a:Implementation Note:"
        )
        addStringOption("Xdoclint:all,-missing", "-quiet")
        addStringOption("Xwerror", "-quiet")
    }
}

testing {
    @Suppress("UnstableApiUsage")
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
            targets.all {
                testTask {
                    group = "build"
                    maxHeapSize = "4g"
                    // Some tests overlap due to using the same temp folders within one project
                    // maxParallelForks = 4 <- set this, once tests can run in parallel
                }
            }
        }

        // Test functionally correct behavior under stress/loads with many repeated iterations.
        register<JvmTestSuite>("hammer") {
            testType.set("hammer")
            targets.all {
                testTask {
                    group = "build"
                    shouldRunAfter(tasks.test)
                    usesService(
                        gradle.sharedServices.registerIfAbsent("lock", TaskLockService::class) {
                            maxParallelUsages = 1
                        }
                    )
                    maxHeapSize = "8g"
                }
            }
        }

        // Tests that normally needs more than 100 ms to be executed.
        register<JvmTestSuite>("timeConsuming") {
            testType.set("time-consuming")
            targets.all {
                testTask {
                    group = "build"
                    shouldRunAfter(tasks.test)
                    maxHeapSize = "16g"
                }
            }
        }

        // integration test suite
        register<JvmTestSuite>("itest") {
            testType.set(TestSuiteType.INTEGRATION_TEST)
            targets.all {
                testTask {
                    group = "build"
                    shouldRunAfter(tasks.test)
                    maxHeapSize = "8g"
                    addTestListener(testLogger())
                }
            }
        }

        // EET for end-to-end tests
        register<JvmTestSuite>("eet") {
            testType.set("end-to-end-test")
            targets.all {
                testTask {
                    group = "build"
                    shouldRunAfter(tasks.test)
                    maxHeapSize = "8g"
                    jvmArgs("-XX:ActiveProcessorCount=6")
                }
            }
        }

        // "cross-service" tests (this suite will be removed)
        register<JvmTestSuite>("xtest") {
            testType.set("cross-service-test")
            targets.all {
                testTask {
                    shouldRunAfter(tasks.test)
                    maxHeapSize = "8g"
                }
            }
        }
    }
}

// If user gave the argument '-PactiveProcessorCount', then do:
// - run all test tasks in sequence
// - give the -XX:ActiveProcessorCount argument to the test JVMs
val activeProcessorCount = providers.gradleProperty("activeProcessorCount")

if (activeProcessorCount.isPresent) {
    tasks.withType<Test>().configureEach {
        usesService(
            gradle.sharedServices.registerIfAbsent("lock", TaskLockService::class) {
                maxParallelUsages = 1
            }
        )
        jvmArgs("-XX:ActiveProcessorCount=${activeProcessorCount.get()}")
    }
}

tasks.jacocoTestReport {
    // Configure Jacoco so it outputs XML reports (needed by SonarCloud)
    reports {
        xml.required = true
        html.required = true
    }
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
    dependsOn(tasks.javadoc)
    dependsOn(tasks.named("hammerClasses"))
    dependsOn(tasks.named("timeConsumingClasses"))
    dependsOn(tasks.named("itestClasses"))
    dependsOn(tasks.named("eetClasses"))
    dependsOn(tasks.named("xtestClasses"))
}

tasks.check { dependsOn(tasks.jacocoTestReport) }

tasks.named("qualityGate") { dependsOn(tasks.checkAllModuleInfo) }

tasks.withType<JavaCompile>() {
    // When ding a 'qualityGate' run, make sure spotlessApply is done before doing compilation and
    // other checks based on compiled code
    mustRunAfter(tasks.spotlessApply)
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

checkstyle { toolVersion = "10.12.7" }

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required = true
        html.required = true
        sarif.required = true
    }
}

// Remove below configuration once all 'TIME_CONSUMING' tests are moved to 'src/timeConsuming'.
tasks.test {
    options {
        this as JUnitPlatformOptions
        excludeTags("TIME_CONSUMING")
    }
}

tasks.withType<JavaExec>().configureEach {
    // Do not yet run things on the '--module-path'
    modularity.inferModulePath = false
    if (name.endsWith("main()")) {
        notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
    }
}
