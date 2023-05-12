import com.adarshr.gradle.testlogger.theme.ThemeType

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
    java
    jacoco
    id("com.gorylenko.gradle-git-properties")
    id("com.swirlds.platform.jpms-modules")
    id("com.swirlds.platform.repositories")
    id("com.swirlds.platform.spotless-conventions")
    id("com.swirlds.platform.spotless-java-conventions")
    id("com.swirlds.platform.spotless-kotlin-conventions")
    id("com.adarshr.test-logger")
}

group = "com.swirlds"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        @Suppress("UnstableApiUsage")
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
    modularity.inferModulePath.set(true)
}

tasks.withType<AbstractArchiveTask> {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    fileMode = 664
    dirMode = 775
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions)
        .tags("apiNote:a:API Note:", "implSpec:a:Implementation Requirements:", "implNote:a:Implementation Note:")
}

testing {
    suites {
        @Suppress("UnstableApiUsage")
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()

            targets {
                all {
                    testTask.configure {
                        options {
                            val jpo = this as JUnitPlatformOptions
                            jpo.excludeTags(
                                "TIME_CONSUMING",
                                "AT_SCALE",
                                "REMOTE_ONLY",
                                "HAMMER",
                                "PERFORMANCE",
                                "PROFILING_ONLY",
                                "INFREQUENT_EXEC_ONLY"
                            )
                        }
                        maxHeapSize = "4g"
                        jvmArgs("-XX:ActiveProcessorCount=16")
                    }
                }
            }
        }

        @Suppress("UNUSED_VARIABLE", "UnstableApiUsage")
        val hammerTest by registering(JvmTestSuite::class) {
            testType.set("hammer-test")
            useJUnitJupiter()

            sources {
                java {
                    setSrcDirs(sourceSets.test.get().java.srcDirs)
                }
                resources {
                    setSrcDirs(sourceSets.test.get().resources.srcDirs)
                }
                compileClasspath += sourceSets.test.get().compileClasspath
                runtimeClasspath += sourceSets.test.get().runtimeClasspath
            }

            dependencies {
                implementation(project)
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        options {
                            val jpo = this as JUnitPlatformOptions
                            jpo.includeTags("HAMMER")
                            jpo.excludeTags("PROFILING_ONLY", "INFREQUENT_EXEC_ONLY")
                        }
                        maxHeapSize = "8g"
                        jvmArgs("-XX:ActiveProcessorCount=16")
                    }
                }
            }

        }

        @Suppress("UNUSED_VARIABLE", "UnstableApiUsage")
        val performanceTest by registering(JvmTestSuite::class) {
            testType.set(TestSuiteType.PERFORMANCE_TEST)
            useJUnitJupiter()

            sources {
                java {
                    setSrcDirs(sourceSets.test.get().java.srcDirs)
                }
                resources {
                    setSrcDirs(sourceSets.test.get().resources.srcDirs)
                }
                compileClasspath += sourceSets.test.get().compileClasspath
                runtimeClasspath += sourceSets.test.get().runtimeClasspath
            }

            dependencies {
                implementation(project)
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                        options {
                            val jpo = this as JUnitPlatformOptions
                            jpo.includeTags("TIME_CONSUMING", "AT_SCALE", "REMOTE_ONLY", "PERFORMANCE")
                            jpo.excludeTags("PROFILING_ONLY", "INFREQUENT_EXEC_ONLY")
                        }

                        setForkEvery(1)
                        minHeapSize = "2g"
                        maxHeapSize = "16g"
                        jvmArgs("-XX:ActiveProcessorCount=16", "-XX:+UseZGC")
                    }
                }
            }

        }
    }
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val testExtension = tasks.test.get().extensions.getByType<JacocoTaskExtension>()
    val hammerExtension = tasks.getByName("hammerTest").extensions.getByType<JacocoTaskExtension>()
    val performanceExtension = tasks.getByName("performanceTest").extensions.getByType<JacocoTaskExtension>()
    executionData.from(
        testExtension.destinationFile,
        hammerExtension.destinationFile,
        performanceExtension.destinationFile
    )

    mustRunAfter(tasks.getByName("hammerTest"))
    mustRunAfter(tasks.getByName("performanceTest"))
    shouldRunAfter(tasks.check)
}

tasks.assemble {
    dependsOn(tasks.testClasses)
    if (tasks.findByName("jmhClasses") != null) {
        dependsOn(tasks.named("jmhClasses"))
    }
}

tasks.check {
    dependsOn(tasks.named<JacocoReport>("jacocoTestReport"))
}

gitProperties {
    keys = listOf("git.build.version", "git.commit.id", "git.commit.id.abbrev")
}

testlogger {
    theme = ThemeType.MOCHA
    slowThreshold = 10000
    showStandardStreams = true
    showPassedStandardStreams = false
    showSkippedStandardStreams = false
    showFailedStandardStreams = true
}

tasks.jar {
    exclude("**/classpath.index")
}