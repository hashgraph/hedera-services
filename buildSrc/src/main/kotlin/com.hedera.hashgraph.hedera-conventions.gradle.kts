import gradle.kotlin.dsl.accessors._de3ff27eccbd9efdc5c099f60a1d8f4c.check
import org.sonarqube.gradle.SonarQubeTask
import java.text.SimpleDateFormat
import java.util.*

/*-
 * ‌
 * Hedera Conventions
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

plugins {
    `java-library`
    `maven-publish`
    jacoco
    id("com.diffplug.spotless")
}

group = "com.hedera.hashgraph"

// Specify the JDK Version and vendor that we will support
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        @Suppress("UnstableApiUsage")
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

// Define the repositories from which we will pull dependencies
repositories {
    mavenLocal()
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
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/comhederahashgraph-1502")
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/comhederahashgraph-1531")
    }
}

spotless {
    // optional: limit format enforcement to just the files changed by this feature branch
    ratchetFrom("origin/master")

    format("misc", {
        // define the files to apply `misc` to
        target("*.gradle", "*.md", ".gitignore")

        // define the steps to apply to those files
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    })
    java({
        // fix errors due to dashed comment blocks (eg: /*-, /*--, etc)
        addStep(RepairDashedCommentsFormatterStep.create())
        // Remove the old license headers as the spotless licenseHeader formatter
        // cannot find them if they are located between the package and import statements.
        addStep(StripOldLicenseFormatterStep.create())
        // enable toggle comment support
        toggleOffOn()
        // don't need to set target, it is inferred from java
        // apply a specific flavor of google-java-format
        googleJavaFormat().aosp().reflowLongStrings()
        // make sure every file has the following copyright header.
        // optionally, Spotless can set copyright years by digging
        // through git history (see "license" section below).
        // The delimiter override below is required to support some
        // of our test classes which are in the default package.
        licenseHeader("""
           /*
            * Copyright (C) ${'$'}YEAR Hedera Hashgraph, LLC
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
        """.trimIndent(), "(package|import)")
    })
}

// Enable maven publications
publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

// Make sure we use UTF-8 encoding when compiling
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

testing {
    suites {
        // Configure the normal unit test suite to use JUnit Jupiter.
        @Suppress("UnstableApiUsage")
        val test by getting(JvmTestSuite::class) {
            // Enable JUnit as our test engine
            useJUnitJupiter()
        }

        // Configure the integration test suite
        @Suppress("UNUSED_VARIABLE", "UnstableApiUsage")
        val itest by registering(JvmTestSuite::class) {
            testType.set(TestSuiteType.INTEGRATION_TEST)
            dependencies {
                implementation(project)
            }

            // "shouldRunAfter" will only make sure if both test and itest are run concurrently,
            // that "test" completes first. If you run "itest" directly, it doesn't force "test" to run.
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }

        // Configure the hammer test suite
        @Suppress("UNUSED_VARIABLE", "UnstableApiUsage")
        val hammer by registering(JvmTestSuite::class) {
            testType.set("hammer-test")
            dependencies {
                implementation(project)
            }

            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(test)
                    }
                }
            }
        }

        // Add the EET task for executing end-to-end tests
        testing {
            suites {
                @Suppress("UnstableApiUsage", "UNUSED_VARIABLE")
                val eet by registering(JvmTestSuite::class) {
                    testType.set("end-to-end-test")
                    dependencies {
                        implementation(project)
                    }

                    // "shouldRunAfter" will only make sure if both test and eet are run concurrently,
                    // that "test" completes first. If you run "eet" directly, it doesn't force "test" to run.
                    targets {
                        all {
                            testTask.configure {
                                shouldRunAfter(tasks.test)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Increase the heap size for the unit tests
tasks.test {
    maxHeapSize = "1024m"
}

tasks.getByName<Test>("itest") {
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

// Configure Jacoco so it outputs XML reports (needed by SonarCloud), and so that it combines the code
// coverage from both unit and integration tests into a single report from `jacocoTestReport`
tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val testExtension: JacocoTaskExtension = tasks.test.get().extensions.getByType<JacocoTaskExtension>()
    val iTestExtension: JacocoTaskExtension = tasks.getByName("itest").extensions.getByType<JacocoTaskExtension>()
    executionData.from(testExtension.destinationFile, iTestExtension.destinationFile)
}

tasks.check {
    dependsOn(tasks.named<JacocoReport>("jacocoTestReport"))
}


