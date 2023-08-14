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
    id("java")
    id("jacoco")
    id("com.hedera.hashgraph.jpms-modules")
    id("com.hedera.hashgraph.jpms-module-dependencies")
    id("com.hedera.hashgraph.repositories")
    id("com.hedera.hashgraph.spotless-conventions")
    id("com.hedera.hashgraph.spotless-java-conventions")
    id("com.hedera.hashgraph.spotless-kotlin-conventions")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        @Suppress("UnstableApiUsage")
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

configurations.all {
    // In case published versions of a module are also available, always prefer the local one
    resolutionStrategy.preferProjectModules()
}

val internal: Configuration = configurations.create("internal") {
    isCanBeConsumed = false
    isCanBeResolved = false
}

dependencies {
    "internal"(platform("com.hedera.hashgraph:hedera-platform"))
}

sourceSets.all {
    configurations.getByName(annotationProcessorConfigurationName) {
        extendsFrom(internal)
    }
    configurations.getByName(compileClasspathConfigurationName) {
        extendsFrom(internal)
    }
    configurations.getByName(runtimeClasspathConfigurationName) {
        extendsFrom(internal)
    }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    fileMode = 664
    dirMode = 775
}

tasks.jar {
    exclude("**/classpath.index")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions)
        .tags("apiNote:a:API Note:", "implSpec:a:Implementation Requirements:", "implNote:a:Implementation Note:")
}

testing {
    @Suppress("UnstableApiUsage")
    suites {
        // Configure the normal unit test suite to use JUnit Jupiter.
        named("test", JvmTestSuite::class) {
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

            // Add 'targets' (tasks of type Test) for the 'test' test suite: 'hammerTest' and 'performanceTest'.
            // Gradle's test suite API does not yet allow to add additional targets there (although it's planned).
            // Thus, we need to go directly via 'tasks.register' and then set 'testClassesDirs' and 'sources' to
            // the information we get from the 'source set' (sources) of this 'test suite'.
            tasks.register<Test>("hammerTest") {
                testClassesDirs = sources.output.classesDirs
                classpath = sources.runtimeClasspath

                shouldRunAfter(tasks.test)

                useJUnitPlatform {
                    includeTags("HAMMER")
                }
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

// Configure Jacoco so it outputs XML reports (needed by SonarCloud), and so that it combines the code
// coverage from both unit and integration tests into a single report from `jacocoTestReport`
tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val hammerData = tasks.named("hammerTest").map {
        it.extensions.getByType<JacocoTaskExtension>().destinationFile!!
    }
    val performanceData = tasks.named("performanceTest").map {
        it.extensions.getByType<JacocoTaskExtension>().destinationFile!!
    }
    val iTestData = tasks.named("itest").map {
        it.extensions.getByType<JacocoTaskExtension>().destinationFile!!
    }

    executionData.from(hammerData, performanceData, iTestData)

    mustRunAfter(tasks.named("hammerTest"))
    mustRunAfter(tasks.named("performanceTest"))
    mustRunAfter(tasks.named("itest"))
}

tasks.assemble {
    dependsOn(tasks.testClasses)
    if (tasks.names.contains("jmhClasses")) {
        dependsOn(tasks.named("jmhClasses"))
    }
}
