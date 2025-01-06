// SPDX-License-Identifier: Apache-2.0
import com.github.jengelman.gradle.plugins.shadow.internal.DefaultDependencyFilter
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("org.hiero.gradle.module.application")
    id("com.gradleup.shadow") version "8.3.0"
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

tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-Xlint:-exports") }

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

tasks.withType<ShadowJar>().configureEach {
    group = "shadow"
    from(sourceSets.main.get().output)
    mergeServiceFiles()

    manifest { attributes("Multi-Release" to "true") }

    // There is an issue in the shadow plugin that it automatically accesses the
    // files in 'runtimeClasspath' while Gradle is building the task graph.
    // See: https://github.com/GradleUp/shadow/issues/882
    dependencyFilter = NoResolveDependencyFilter()
}

application.mainClass = "com.hedera.services.bdd.suites.SuiteRunner"

tasks.shadowJar { archiveFileName.set("SuiteRunner.jar") }

val yahCliJar =
    tasks.register<ShadowJar>("yahCliJar") {
        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))
        from(sourceSets["yahcli"].output)
        archiveClassifier.set("yahcli")
        configurations = listOf(project.configurations.getByName("yahcliRuntimeClasspath"))

        manifest { attributes("Main-Class" to "com.hedera.services.yahcli.Yahcli") }
    }

val rcdiffJar =
    tasks.register<ShadowJar>("rcdiffJar") {
        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))
        from(sourceSets["main"].output)
        from(sourceSets["rcdiff"].output)
        destinationDirectory.set(project.file("rcdiff"))
        archiveFileName.set("rcdiff.jar")
        configurations = listOf(project.configurations.getByName("rcdiffRuntimeClasspath"))

        manifest { attributes("Main-Class" to "com.hedera.services.rcdiff.RcDiffCmdWrapper") }
    }

val validationJar =
    tasks.register<ShadowJar>("validationJar") {
        exclude(listOf("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/INDEX.LIST"))

        archiveFileName.set("ValidationScenarios.jar")

        manifest {
            attributes(
                "Main-Class" to
                    "com.hedera.services.bdd.suites.utils.validation.ValidationScenarios"
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

class NoResolveDependencyFilter : DefaultDependencyFilter(project) {
    override fun resolve(configuration: FileCollection): FileCollection {
        return configuration
    }
}
