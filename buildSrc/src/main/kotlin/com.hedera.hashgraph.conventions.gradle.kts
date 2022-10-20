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
import gradle.kotlin.dsl.accessors._de3ff27eccbd9efdc5c099f60a1d8f4c.check
import java.text.SimpleDateFormat
import java.util.*

plugins {
    `java-library`
    jacoco
    id("com.hedera.hashgraph.spotless-conventions")
    id("com.hedera.hashgraph.spotless-java-conventions")
    id("com.hedera.hashgraph.spotless-kotlin-conventions")
    id("org.gradlex.extra-java-module-info")
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

extraJavaModuleInfo {
    failOnMissingModuleInfo.set(true)
    automaticModule("javax.inject:javax.inject", "javax.inject")
    automaticModule("grpc-protobuf:grpc-protobuf", "grpc-protobuf")
    automaticModule("org.hyperledger.besu.internal:crypto", "crypto")
    automaticModule("org.hyperledger.besu:secp256k1", "secp256k1")
    automaticModule("org.hyperledger.besu.internal:rlp", "rlp")
    automaticModule("org.hyperledger.besu:plugin-api", "plugin-api")
    automaticModule("com.offbynull.portmapper:portmapper", "portmapper")
    automaticModule("io.grpc:grpc-netty", "grpc-netty")
    automaticModule("com.google.code.findbugs:jsr305", "jsr305")
    automaticModule("com.google.j2objc:j2objc-annotations", "j2objc-annotations")
    automaticModule("io.perfmark:perfmark-api", "perfmark-api")
    automaticModule("junit:junit", "junit")
    automaticModule("io.opencensus:opencensus-api", "opencensus-api")
    automaticModule("io.grpc:grpc-context", "grpc-context")
    automaticModule("org.jetbrains.kotlin:kotlin-stdlib-common", "kotlin-stdlib-common")
    automaticModule("com.google.android:annotations", "annotations")
    automaticModule("org.codehaus.mojo:animal-sniffer-annotations", "animal-sniffer-annotations")
    automaticModule("com.google.guava:listenablefuture", "listenablefuture")
    automaticModule("com.google.guava:failureaccess", "failureaccess")
    automaticModule("org.connid:framework-internal", "framework-internal")
    automaticModule("org.connid:framework", "framework")
    automaticModule("org.openjfx:javafx-base", "javafx-base")
    automaticModule("io.grpc:grpc-api", "grpc-api")
    automaticModule("io.grpc:grpc-protobuf-lite", "protobuf-lite")
    automaticModule("io.grpc:grpc-core", "grpc-core")
    automaticModule("io.grpc:grpc-stub", "grpc-stub")
    automaticModule("io.grpc:grpc-stub", "grpc-stub")
    automaticModule("io.grpc:grpc-testing", "grpc-testing")
    automaticModule("org.hamcrest:hamcrest-core", "hamcrest-core")
    automaticModule("org.hyperledger.besu:blake2bf", "blake2bf")
    automaticModule("org.hyperledger.besu:secp256r1", "secp256r1")
    automaticModule("com.goterl:resource-loader", "resource-loader")
    automaticModule("com.goterl:lazysodium-java", "lazysodium-java")
    automaticModule("org.jetbrains:annotations", "annotations")
    automaticModule("com.google.api.grpc:proto-google-common-protos", "proto-google-common-protos")
    automaticModule("io.grpc:grpc-protobuf", "grpc-protobuf")
    automaticModule("org.hyperledger.besu:bls12-381", "bls12-381")
    automaticModule("hapi-utils-0.31.0-SNAPSHOT.jar", "hapi-utils")
    automaticModule("hapi-fees-0.31.0-SNAPSHOT.jar", "hapi-fees")
//    automaticModule("hedera-evm-api-0.31.0-SNAPSHOT.jar", "hedera-evm-api")

    automaticModule("com.hedera.hashgraph:ethereumj-core", "ethereumj-core")
    automaticModule("org.testcontainers:testcontainers", "testcontainers")
    automaticModule("com.github.docker-java:docker-java-api", "docker-java-api")
    automaticModule("org.rnorth.duct-tape:duct-tape", "duct-tape")
    automaticModule("com.github.docker-java:docker-java-transport-zerodep", "docker-java-transport-zerodep")
    automaticModule("com.madgag.spongycastle:prov", "prov")
    automaticModule("com.madgag.spongycastle:core", "core")
    automaticModule("org.springframework:spring-context", "spring-context")
    automaticModule("com.typesafe:config", "config")
    automaticModule("com.googlecode.concurrent-locks:concurrent-locks", "concurrent-lockst")
    automaticModule("org.springframework:spring-aop", "spring-aop")
    automaticModule("org.springframework:spring-beans", "spring-beans")
    automaticModule("org.springframework:spring-expression", "spring-expression")
    automaticModule("org.springframework:spring-core", "spring-core")
    automaticModule("com.github.docker-java:docker-java-transport", "docker-java-transport")
    automaticModule("commons-logging:commons-logging", "commons-logging")
    automaticModule("org.awaitility:awaitility", "awaitility")
    automaticModule("com.google.truth.extensions:truth-java8-extension", "truth-java8-extension")
    automaticModule("com.google.truth:truth", "truth")
    automaticModule("com.google.auto.value:auto-value-annotations", "auto-value-annotations")
    automaticModule("org.hyperledger.besu.internal:util", "util")
    automaticModule("commons-codec:commons-codec", "commons-codec")
}

// Make sure we use UTF-8 encoding when compiling
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
}

tasks.withType<Jar> {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    fileMode = 664
    dirMode = 775
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
    if (tasks.findByName("jmhClasses") != null) {
        dependsOn(tasks.named("jmhClasses"))
    }
}
