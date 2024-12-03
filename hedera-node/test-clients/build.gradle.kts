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

mainModuleInfo {
    runtimeOnly("org.junit.jupiter.engine")
    runtimeOnly("org.junit.platform.launcher")
    runtimeOnly("org.hiero.event.creator")
    runtimeOnly("org.hiero.event.creator.impl")
}

testModuleInfo { runtimeOnly("org.junit.jupiter.api") }

sourceSets {
    create("rcdiff")
    create("yahcli")
}

tasks.register<JavaExec>("runTestClient") {
    group = "build"
    description = "Run a test client via -PtestClient=<Class>"

    classpath = sourceSets.main.get().runtimeClasspath + files(tasks.jar)
    mainClass = providers.gradleProperty("testClient")
}

tasks.jacocoTestReport {
    classDirectories.setFrom(files(project(":app").layout.buildDirectory.dir("classes/java/main")))
    sourceDirectories.setFrom(files(project(":app").projectDir.resolve("src/main/java")))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.test {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    // Unlike other tests, these intentionally corrupt embedded state to test FAIL_INVALID
    // code paths; hence we do not run LOG_VALIDATION after the test suite finishes
    useJUnitPlatform { includeTags("(INTEGRATION|STREAM_VALIDATION)") }

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
    // Tell our launcher to target an embedded network whose mode is set per-class
    systemProperty("hapi.spec.embedded.mode", "per-class")

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

val prCheckTags =
    mapOf(
        "hapiTestAdhoc" to "ADHOC",
        "hapiTestCrypto" to "CRYPTO",
        "hapiTestToken" to "TOKEN",
        "hapiTestRestart" to "RESTART|UPGRADE",
        "hapiTestSmartContract" to "SMART_CONTRACT",
        "hapiTestNDReconnect" to "ND_RECONNECT",
        "hapiTestTimeConsuming" to "LONG_RUNNING",
        "hapiTestMisc" to
            "!(INTEGRATION|CRYPTO|TOKEN|RESTART|UPGRADE|SMART_CONTRACT|ND_RECONNECT|LONG_RUNNING)"
    )
val prCheckStartPorts =
    mapOf(
        "hapiTestAdhoc" to "25000",
        "hapiTestCrypto" to "26000",
        "hapiTestToken" to "27000",
        "hapiTestRestart" to "28000",
        "hapiTestSmartContract" to "29000",
        "hapiTestNDReconnect" to "30000",
        "hapiTestTimeConsuming" to "31000",
        "hapiTestMisc" to "32000"
    )

tasks {
    prCheckTags.forEach { (taskName, _) -> register(taskName) { dependsOn("testSubprocess") } }
}

tasks.register<Test>("testSubprocess") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    val ciTagExpression =
        gradle.startParameter.taskNames
            .stream()
            .map { prCheckTags[it] ?: "" }
            .filter { it.isNotBlank() }
            .toList()
            .joinToString("|")
    useJUnitPlatform {
        includeTags(
            if (ciTagExpression.isBlank()) "none()|!(EMBEDDED|REPEATABLE)"
            else "(${ciTagExpression}|STREAM_VALIDATION|LOG_VALIDATION)&!(EMBEDDED|REPEATABLE)"
        )
    }

    // Choose a different initial port for each test task if running as PR check
    val initialPort =
        gradle.startParameter.taskNames
            .stream()
            .map { prCheckStartPorts[it] ?: "" }
            .filter { it.isNotBlank() }
            .findFirst()
            .orElse("")
    systemProperty("hapi.spec.initial.port", initialPort)

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

val prEmbeddedCheckTags =
    mapOf(
        "hapiEmbeddedMisc" to "EMBEDDED",
    )

tasks {
    prEmbeddedCheckTags.forEach { (taskName, _) ->
        register(taskName) { dependsOn("testEmbedded") }
    }
}

// Runs tests against an embedded network that supports concurrent tests
tasks.register<Test>("testEmbedded") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    val ciTagExpression =
        gradle.startParameter.taskNames
            .stream()
            .map { prEmbeddedCheckTags[it] ?: "" }
            .filter { it.isNotBlank() }
            .toList()
            .joinToString("|")
    useJUnitPlatform {
        includeTags(
            if (ciTagExpression.isBlank()) "none()|!(RESTART|ND_RECONNECT|UPGRADE|REPEATABLE)"
            else "(${ciTagExpression}|STREAM_VALIDATION|LOG_VALIDATION)&(!INTEGRATION)"
        )
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
    // Tell our launcher to target a concurrent embedded network
    systemProperty("hapi.spec.embedded.mode", "concurrent")

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
}

val prRepeatableCheckTags =
    mapOf(
        "hapiRepeatableMisc" to "REPEATABLE",
    )

tasks {
    prRepeatableCheckTags.forEach { (taskName, _) ->
        register(taskName) { dependsOn("testRepeatable") }
    }
}

// Runs tests against an embedded network that achieves repeatable results by running tests in a
// single thread
tasks.register<Test>("testRepeatable") {
    testClassesDirs = sourceSets.main.get().output.classesDirs
    classpath = sourceSets.main.get().runtimeClasspath

    val ciTagExpression =
        gradle.startParameter.taskNames
            .stream()
            .map { prRepeatableCheckTags[it] ?: "" }
            .filter { it.isNotBlank() }
            .toList()
            .joinToString("|")
    useJUnitPlatform {
        includeTags(
            if (ciTagExpression.isBlank())
                "none()|!(RESTART|ND_RECONNECT|UPGRADE|EMBEDDED|NOT_REPEATABLE)"
            else "(${ciTagExpression}|STREAM_VALIDATION|LOG_VALIDATION)&(!INTEGRATION)"
        )
    }

    // Disable all parallelism
    systemProperty("junit.jupiter.execution.parallel.enabled", false)
    systemProperty(
        "junit.jupiter.testclass.order.default",
        "org.junit.jupiter.api.ClassOrderer\$OrderAnnotation"
    )
    // Tell our launcher to target a repeatable embedded network
    systemProperty("hapi.spec.embedded.mode", "repeatable")

    // Limit heap and number of processors
    maxHeapSize = "8g"
    jvmArgs("-XX:ActiveProcessorCount=6")

    // Do not yet run things on the '--module-path'
    modularity.inferModulePath.set(false)
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
        from(sourceSets["main"].output)
        from(sourceSets["rcdiff"].output)
        destinationDirectory.set(project.file("rcdiff"))
        archiveFileName.set("rcdiff.jar")
        configurations = listOf(project.configurations.getByName("rcdiffRuntimeClasspath"))

        manifest {
            attributes(
                "Main-Class" to "com.hedera.services.rcdiff.RcDiffCmdWrapper",
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

tasks.clean {
    dependsOn(cleanYahCli)
    dependsOn(cleanValidation)
}
