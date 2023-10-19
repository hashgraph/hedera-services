/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.conventions")
    id("com.hedera.hashgraph.benchmark-conventions")
    id("java-test-fixtures")
}

description = "Hedera Application - Implementation"

mainModuleInfo {
    annotationProcessor("dagger.compiler")

    // This is needed to pick up and include the native libraries for the netty epoll transport
    runtimeOnly("io.netty.transport.epoll")
}

testModuleInfo {
    requires("com.hedera.node.app")
    requires("com.hedera.node.app.spi.test.fixtures")
    requires("com.hedera.node.config.test.fixtures")
    requires("com.google.jimfs")
    requires("com.swirlds.test.framework")
    requires("io.github.classgraph")
    requires("org.assertj.core")
    requires("org.hamcrest")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    requires("org.mockito")
    requires("org.mockito.junit.jupiter")
    requires("uk.org.webcompere.systemstubs.core")
    requires("uk.org.webcompere.systemstubs.jupiter")
    requiresStatic("com.github.spotbugs.annotations")
}

itestModuleInfo {
    requires("com.hedera.node.app")
    requires("com.hedera.node.app.spi")
    requires("com.hedera.node.app.spi.test.fixtures")
    requires("com.hedera.node.config")
    requires("com.hedera.node.config.test.fixtures")
    requires("com.hedera.node.hapi")
    requires("com.github.spotbugs.annotations")
    requires("com.hedera.pbj.runtime")
    requires("com.swirlds.common")
    requires("com.swirlds.config.api")
    requires("grpc.netty")
    requires("grpc.stub")
    requires("io.grpc")
    requires("io.netty.transport.epoll")
    requires("org.apache.logging.log4j")
    requires("org.assertj.core")
    requires("org.bouncycastle.provider")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
}

xtestModuleInfo {
    annotationProcessor("dagger.compiler")
    requires("com.hedera.node.app")
    requires("com.hedera.node.app.test.fixtures")
    requires("com.hedera.node.app.hapi.fees")
    requires("com.hedera.node.app.hapi.utils")
    requires("com.hedera.node.app.service.consensus.impl")
    requires("com.hedera.node.app.service.contract.impl")
    requires("com.hedera.node.app.service.file.impl")
    requires("com.hedera.node.app.service.mono")
    requires("com.hedera.node.app.service.network.admin.impl")
    requires("com.hedera.node.app.service.schedule.impl")
    requires("com.hedera.node.app.service.token")
    requires("com.hedera.node.app.service.token.impl")
    requires("com.hedera.node.app.service.util.impl")
    requires("com.hedera.node.app.spi")
    requires("com.hedera.node.app.spi.test.fixtures")
    requires("com.hedera.node.config")
    requires("com.hedera.node.config.test.fixtures")
    requires("com.hedera.node.hapi")
    requires("com.github.spotbugs.annotations")
    requires("com.hedera.pbj.runtime")
    requires("com.swirlds.common")
    requires("com.swirlds.config.api")
    requires("com.swirlds.test.framework")
    requires("dagger")
    requires("headlong")
    requires("io.netty.transport.epoll")
    requires("javax.inject")
    requires("org.assertj.core")
    requires("org.hyperledger.besu.datatypes")
    requires("org.hyperledger.besu.evm")
    requires("org.junit.jupiter.api")
    requires("org.mockito")
    requires("org.mockito.junit.jupiter")
    requires("tuweni.bytes")
}

jmhModuleInfo {
    requires("com.hedera.node.app")
    requires("com.hedera.node.app.service.mono")
    requires("com.hedera.node.app.spi.test.fixtures")
    requires("com.hedera.node.hapi")
    requires("com.hedera.pbj.runtime")
    requires("com.swirlds.common")
    requires("jmh.core")
}

tasks.withType<Test> {
    testLogging.exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
}

// Add all the libs dependencies into the jar manifest!
tasks.jar {
    inputs.files(configurations.runtimeClasspath)
    manifest {
        attributes(
            "Main-Class" to "com.hedera.node.app.ServicesMain",
            "Class-Path" to
                configurations.runtimeClasspath.get().elements.map { entry ->
                    entry
                        .map { "../../data/lib/" + it.asFile.name }
                        .sorted()
                        .joinToString(separator = " ")
                }
        )
    }
}

// Copy dependencies into `data/lib`
val copyLib =
    tasks.register<Copy>("copyLib") {
        from(project.configurations.getByName("runtimeClasspath"))
        into(layout.projectDirectory.dir("../data/lib"))
    }

// Copy built jar into `data/apps` and rename HederaNode.jar
val copyApp =
    tasks.register<Copy>("copyApp") {
        from(tasks.jar)
        into(layout.projectDirectory.dir("../data/apps"))
        rename { "HederaNode.jar" }
        shouldRunAfter(tasks.named("copyLib"))
    }

tasks.assemble {
    dependsOn(copyLib)
    dependsOn(copyApp)
}

// Create the "run" task for running a Hedera consensus node
tasks.register<JavaExec>("run") {
    group = "application"
    dependsOn(tasks.assemble)
    workingDir = layout.projectDirectory.dir("..").asFile
    jvmArgs = listOf("-cp", "data/lib/*")
    mainClass.set("com.swirlds.platform.Browser")
}

tasks.register<JavaExec>("modrun") {
    group = "application"
    dependsOn(tasks.assemble)
    workingDir = layout.projectDirectory.dir("..").asFile
    jvmArgs = listOf("-cp", "data/lib/*", "-Dhedera.workflows.enabled=true")
    mainClass.set("com.swirlds.platform.Browser")
}

val cleanRun =
    tasks.register<Delete>("cleanRun") {
        val prjDir = layout.projectDirectory.dir("..")
        delete(prjDir.dir("database"))
        delete(prjDir.dir("output"))
        delete(prjDir.dir("settingsUsed.txt"))
        delete(prjDir.dir("swirlds.jar"))
        delete(prjDir.asFileTree.matching { include("MainNetStats*") })
        val dataDir = prjDir.dir("data")
        delete(dataDir.dir("accountBalances"))
        delete(dataDir.dir("apps"))
        delete(dataDir.dir("lib"))
        delete(dataDir.dir("recordstreams"))
        delete(dataDir.dir("saved"))
    }

tasks.clean { dependsOn(cleanRun) }

tasks.register("showHapiVersion") { doLast { println(libs.versions.hapi.proto.get()) } }

var updateDockerEnvTask =
    tasks.register<Exec>("updateDockerEnv") {
        description =
            "Creates the .env file in the docker folder that contains environment variables for docker"
        group = "docker"

        workingDir(layout.projectDirectory.dir("../docker"))
        commandLine("./update-env.sh", project.version)
    }

tasks.register<Exec>("createDockerImage") {
    description = "Creates the docker image of the services based on the current version"
    group = "docker"

    dependsOn(updateDockerEnvTask, tasks.assemble)
    workingDir(layout.projectDirectory.dir("../docker"))
    commandLine("./docker-build.sh", project.version, layout.projectDirectory.dir("..").asFile)
}

tasks.register<Exec>("startDockerContainers") {
    description = "Starts docker containers of the services based on the current version"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir(layout.projectDirectory.dir("../docker"))
    commandLine("docker-compose", "up")
}

tasks.register<Exec>("stopDockerContainers") {
    description = "Stops running docker containers of the services"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir(layout.projectDirectory.dir("../docker"))
    commandLine("docker-compose", "stop")
}
