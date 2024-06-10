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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.hedera.gradle.services")
    id("com.hedera.gradle.shadow-jar")
}

description = "Hedera Services Test Clients for End to End Tests (EET)"

// Remove the following line to enable all 'javac' lint checks that we have turned on by default
// and then fix the reported issues.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-exports,-lossy-conversions,-text-blocks,-varargs,-static")
}

mainModuleInfo {
    runtimeOnly("org.junit.jupiter.engine")
    runtimeOnly("org.junit.platform.launcher")
}

itestModuleInfo {
    requires("com.hedera.node.test.clients")
    requires("org.apache.commons.lang3")
    requires("org.junit.jupiter.api")
    requires("org.testcontainers")
    requires("org.testcontainers.junit.jupiter")
    requires("org.apache.commons.lang3")
}

eetModuleInfo {
    requires("com.hedera.node.test.clients")
    requires("org.junit.jupiter.api")
    requires("org.testcontainers")
    requires("org.testcontainers.junit.jupiter")
}

sourceSets {
    // Needed because "resource" directory is misnamed. See
    // https://github.com/hashgraph/hedera-services/issues/3361
    main { resources { srcDir("src/main/resource") } }

    create("rcdiff")
    create("yahcli")
}

val ciCheckTagExpressions =
    mapOf(
        "hapiTestCrypto" to "CRYPTO",
        "hapiTestToken" to "TOKEN",
        "hapiTestRestart" to "RESTART",
        "hapiTestSmartContract" to "SMART_CONTRACT",
        "hapiTestNDReconnect" to "ND_RECONNECT",
        "hapiTestTimeConsuming" to "LONG_RUNNING",
        "hapiTestMisc" to "!(CRYPTO|TOKEN|SMART_CONTRACT|LONG_RUNNING|RESTART|ND_RECONNECT)"
    )

tasks {
    ciCheckTagExpressions.forEach { (taskName, _) -> register(taskName) { dependsOn("test") } }
}

tasks.test {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    val ciTagExpression =
        gradle.startParameter.taskNames
            .stream()
            .map { ciCheckTagExpressions[it] ?: "" }
            .filter { it.isNotBlank() }
            .toList()
            .joinToString("|")
    useJUnitPlatform {
        includeTags(
            if (ciTagExpression.isBlank()) "any()|none()"
            else "${ciTagExpression}|STREAM_VALIDATION|LOG_VALIDATION"
        )
    }

    // Default quiet mode is "false" unless we are running in CI or set it explicitly to "true"
    systemProperty(
        "hapi.spec.quiet.mode",
        System.getProperty("hapi.spec.quiet.mode")
            ?: if (ciTagExpression.isNotBlank()) "true" else "false"
    )
    systemProperty("junit.jupiter.execution.parallel.enabled", true)
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    // Surprisingly, the Gradle JUnitPlatformTestExecutionListener fails to gather result
    // correctly if test classes run in parallel (concurrent execution WITHIN a test class
    // is fine). So we need to force the test classes to run in the same thread. Luckily this
    // is not a huge limitation, as our test classes generally have enough non-leaky tests to
    // get a material speed up. See https://github.com/gradle/gradle/issues/6453.
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
    systemProperty(
        "junit.jupiter.testclass.order.default",
        "org.junit.jupiter.api.ClassOrderer\$OrderAnnotation"
    )

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")
    maxParallelForks = 1

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

// Runs a test against an embedded network; when we have deterministic clients
// we will add a testDeterministicEmbedded for completely reproducible streams
tasks.register<Test>("testEmbedded") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    useJUnitPlatform {
        // Exclude tests that start and stop nodes, hence must run against a subprocess network
        excludeTags("RESTART|ND_RECONNECT")
    }

    systemProperty("junit.jupiter.execution.parallel.enabled", true)
    systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
    // Surprisingly, the Gradle JUnitPlatformTestExecutionListener fails to gather result
    // correctly if test classes run in parallel (concurrent execution WITHIN a test class
    // is fine). So we need to force the test classes to run in the same thread. Luckily this
    // is not a huge limitation, as our test classes generally have enough non-leaky tests to
    // get a material speed up. See https://github.com/gradle/gradle/issues/6453.
    systemProperty("junit.jupiter.execution.parallel.mode.classes.default", "same_thread")
    systemProperty(
        "junit.jupiter.testclass.order.default",
        "org.junit.jupiter.api.ClassOrderer\$OrderAnnotation"
    )
    // Tell our launcher to target an embedded network
    systemProperty("hapi.spec.embedded.mode", true)

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

tasks.itest {
    systemProperty("itests", System.getProperty("itests"))
    systemProperty("junit.jupiter.execution.parallel.enabled", false)
    systemProperty("TAG", "services-node:" + project.version)
    systemProperty("networkWorkspaceDir", layout.buildDirectory.dir("network/itest").get().asFile)
}

tasks.eet {
    systemProperty("TAG", "services-node:" + project.version)
    systemProperty("networkWorkspaceDir", layout.buildDirectory.dir("network/itest").get().asFile)
}

tasks.shadowJar {
    archiveFileName.set("SuiteRunner.jar")

    manifest {
        attributes(
            "Main-Class" to "com.hedera.services.bdd.suites.SuiteRunner",
            "Multi-Release" to "true"
        )
    }
}

val yahCliJar =
    tasks.register<ShadowJar>("yahCliJar") {
        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))
        from(sourceSets["yahcli"].output)
        archiveClassifier.set("yahcli")
        configurations = listOf(project.configurations.getByName("yahcliRuntimeClasspath"))

        manifest {
            attributes(
                "Main-Class" to "com.hedera.services.yahcli.Yahcli",
                "Multi-Release" to "true"
            )
        }
    }

val rcdiffJar =
    tasks.register<ShadowJar>("rcdiffJar") {
        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))
        from(sourceSets["rcdiff"].output)
        destinationDirectory.set(project.file("rcdiff"))
        archiveFileName.set("rcdiff.jar")
        configurations = listOf(project.configurations.getByName("rcdiffRuntimeClasspath"))

        manifest {
            attributes(
                "Main-Class" to "com.hedera.services.rcdiff.RcDiff",
                "Multi-Release" to "true"
            )
        }
    }

val validationJar =
    tasks.register<ShadowJar>("validationJar") {
        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))

        archiveFileName.set("ValidationScenarios.jar")

        manifest {
            attributes(
                "Main-Class" to
                    "com.hedera.services.bdd.suites.utils.validation.ValidationScenarios",
                "Multi-Release" to "true"
            )
        }
    }

val copyValidation =
    tasks.register<Copy>("copyValidation") {
        group = "copy"
        from(validationJar)
        into(project.file("validation-scenarios"))
    }

val cleanValidation =
    tasks.register<Delete>("cleanValidation") {
        group = "copy"
        delete(File(project.file("validation-scenarios"), "ValidationScenarios.jar"))
    }

val copyYahCli =
    tasks.register<Copy>("copyYahCli") {
        group = "copy"
        from(yahCliJar)
        into(project.file("yahcli"))
        rename { "yahcli.jar" }
    }

val cleanYahCli =
    tasks.register<Delete>("cleanYahCli") {
        group = "copy"
        delete(File(project.file("yahcli"), "yahcli.jar"))
    }

tasks.assemble {
    dependsOn(tasks.shadowJar)
    dependsOn(copyYahCli)
}

tasks.clean {
    dependsOn(cleanYahCli)
    dependsOn(cleanValidation)
}
