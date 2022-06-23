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
    id("org.sonarqube")
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
    }
}
