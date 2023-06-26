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
import java.text.SimpleDateFormat
import java.util.Date

plugins {
    `java-library`
    jacoco
    id("com.hedera.hashgraph.spotless-conventions")
    id("com.hedera.hashgraph.spotless-java-conventions")
    id("com.hedera.hashgraph.spotless-kotlin-conventions")
    id("com.hedera.hashgraph.jpms-modules")
}

group = "com.hedera.hashgraph"

// Specify the JDK Version and vendor that we will support
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

// Define the repositories from which we will pull dependencies
repositories {
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
    maven {
        url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-prerelease-channel")
    }
    maven {
        url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-develop-commits")
    }
    maven {
        url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-adhoc-commits")
    }
    maven {
        url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-develop-daily-snapshots")
    }
    maven {
        url = uri("https://us-maven.pkg.dev/swirlds-registry/maven-develop-snapshots")
    }
    maven {
        url = uri("https://hyperledger.jfrog.io/artifactory/besu-maven")
        content { includeGroupByRegex("org\\.hyperledger\\..*") }
    }
    maven {
        url = uri("https://artifacts.consensys.net/public/maven/maven/")
        content { includeGroupByRegex("tech\\.pegasys(\\..*)?") }
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/comhederahashgraph-1502")
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/comhederahashgraph-1531")
    }
}

val internal: Configuration = configurations.create("internal") {
    isCanBeConsumed = false
    isCanBeResolved = false
}

dependencies {
    "internal"(platform("com.hedera.hashgraph:hedera-platform"))
}

javaModuleDependencies {
    versionsFromConsistentResolution(":app")
}

configurations.getByName("mainRuntimeClasspath") {
    extendsFrom(internal)
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

// Make sure we use UTF-8 encoding when compiling
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Jar>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    fileMode = 664
    dirMode = 775
}

testing {
    suites {
        // Configure the normal unit test suite to use JUnit Jupiter.
        named("test", JvmTestSuite::class) {
            // Enable JUnit as our test engine
            useJUnitJupiter()
            targets.all {
                testTask {
                    // Increase the heap size for the unit tests
                    maxHeapSize = "4096m"
                    // Can be useful to set in some cases
                    // testLogging.showStandardStreams = true
                }
            }
        }

        // Configure the integration test suite
        register<JvmTestSuite>("itest") {
            testType.set(TestSuiteType.INTEGRATION_TEST)

            // "shouldRunAfter" will only make sure if both test and itest are run concurrently,
            // that "test" completes first. If you run "itest" directly, it doesn't force "test" to run.
            targets.all {
                testTask {
                    shouldRunAfter(tasks.test)

                    addTestListener(object : TestListener {
                        override fun beforeSuite(suite: TestDescriptor) {
                            logger.lifecycle("=====> Starting Suite: " + suite.displayName + " <=====")
                        }

                        override fun beforeTest(testDescriptor: TestDescriptor) {}
                        override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                            logger.lifecycle(
                                SimpleDateFormat.getDateTimeInstance()
                                    .format(Date()) + ": " + testDescriptor.displayName + " " + result.resultType.name
                            )
                        }

                        override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
                    })
                }
            }
        }

        // Configure the hammer test suite
        register<JvmTestSuite>("hammer") {
            testType.set("hammer-test")

            targets.all {
                testTask {
                    shouldRunAfter(tasks.test)
                }
            }
        }

        // Add the EET task for executing end-to-end tests
        register<JvmTestSuite>("eet") {
            testType.set("end-to-end-test")

            // "shouldRunAfter" will only make sure if both test and eet are run concurrently,
            // that "test" completes first. If you run "eet" directly, it doesn't force "test" to run.
            targets.all {
                testTask {
                    shouldRunAfter(tasks.test)
                }
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

    val testExtension: JacocoTaskExtension =
        tasks.test.get().extensions.getByType<JacocoTaskExtension>()
    val iTestExtension: JacocoTaskExtension =
        tasks.getByName("itest").extensions.getByType<JacocoTaskExtension>()
    executionData.from(testExtension.destinationFile, iTestExtension.destinationFile)
}

tasks.check {
    dependsOn(tasks.named<JacocoReport>("jacocoTestReport"))
}

tasks.assemble {
    dependsOn(tasks.testClasses)
    if (tasks.names.contains("jmhClasses")) {
        dependsOn(tasks.named("jmhClasses"))
    }
}
