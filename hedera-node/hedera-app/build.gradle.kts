/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.java-test-fixtures")
}

description = "Hedera Application - Implementation"

mainModuleInfo {
    annotationProcessor("dagger.compiler")
    annotationProcessor("com.google.auto.service.processor")

    // This is needed to pick up and include the native libraries for the netty epoll transport
    runtimeOnly("io.netty.transport.epoll.linux.x86_64")
    runtimeOnly("io.netty.transport.epoll.linux.aarch_64")
}

testModuleInfo {
    requires("com.fasterxml.jackson.databind")
    requires("com.hedera.node.app")
    requires("com.hedera.node.app.spi.test.fixtures")
    requires("com.hedera.node.config.test.fixtures")
    requires("com.google.jimfs")
    requires("com.swirlds.config.extensions.test.fixtures")
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
    requires("com.swirlds.metrics.api")
    requires("grpc.netty")
    requires("grpc.stub")
    requires("io.grpc")
    requires("org.apache.logging.log4j")
    requires("org.assertj.core")
    requires("org.bouncycastle.provider")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
    runtimeOnly("io.netty.transport.epoll.linux.x86_64")
    runtimeOnly("io.netty.transport.epoll.linux.aarch_64")
}

xtestModuleInfo {
    annotationProcessor("dagger.compiler")
    requires("com.hedera.node.app")
    requires("com.hedera.node.app.test.fixtures")
    requires("com.hedera.node.app.hapi.fees")
    requires("com.hedera.node.app.hapi.utils")
    requires("com.hedera.node.app.service.consensus.impl")
    requires("com.hedera.node.app.service.contract.impl")
    requires("com.hedera.node.app.service.file")
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
    requires("com.swirlds.config.extensions.test.fixtures")
    requires("com.swirlds.metrics.api")
    requires("com.swirlds.platform.core")
    requires("dagger")
    requires("headlong")
    requires("javax.inject")
    requires("org.assertj.core")
    requires("org.hyperledger.besu.datatypes")
    requires("org.hyperledger.besu.evm")
    requires("org.junit.jupiter.api")
    requires("org.mockito")
    requires("org.mockito.junit.jupiter")
    requires("tuweni.bytes")
    runtimeOnly("io.netty.transport.epoll.linux.x86_64")
    runtimeOnly("io.netty.transport.epoll.linux.aarch_64")
}

jmhModuleInfo {
    requires("com.hedera.node.app")
    requires("com.hedera.node.app.service.mono")
    requires("com.hedera.node.app.spi.test.fixtures")
    requires("com.hedera.node.app.test.fixtures")
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
    manifest { attributes("Main-Class" to "com.hedera.node.app.ServicesMain") }
    doFirst {
        manifest.attributes(
            "Class-Path" to
                inputs.files
                    .filter { it.extension == "jar" }
                    .map { "../../data/lib/" + it.name }
                    .sorted()
                    .joinToString(separator = " ")
        )
    }
}

// Copy dependencies into `data/lib`
val copyLib =
    tasks.register<Sync>("copyLib") {
        from(project.configurations.getByName("runtimeClasspath"))
        into(layout.projectDirectory.dir("../data/lib"))
    }

// Copy built jar into `data/apps` and rename HederaNode.jar
val copyApp =
    tasks.register<Sync>("copyApp") {
        from(tasks.jar)
        into(layout.projectDirectory.dir("../data/apps"))
        rename { "HederaNode.jar" }
        shouldRunAfter(tasks.named("copyLib"))
    }

// Working directory for 'run' tasks
val nodeWorkingDir = layout.buildDirectory.dir("node")

val copyNodeData =
    tasks.register<Sync>("copyNodeDataAndConfig") {
        into(nodeWorkingDir)

        // Copy things from hedera-node/data
        into("data/lib") { from(copyLib) }
        into("data/apps") { from(copyApp) }
        into("data/onboard") { from(layout.projectDirectory.dir("../data/onboard")) }
        into("data/keys") { from(layout.projectDirectory.dir("../data/keys")) }

        // Copy hedera-node/configuration/dev as hedera-node/hedera-app/build/node/data/config  }
        from(layout.projectDirectory.dir("../configuration/dev")) { into("data/config") }
        from(layout.projectDirectory.file("../config.txt"))
        from(layout.projectDirectory.file("../log4j2.xml"))
        from(layout.projectDirectory.file("../configuration/dev/settings.txt"))
    }

tasks.assemble {
    dependsOn(copyLib)
    dependsOn(copyApp)
    dependsOn(copyNodeData)
}

// Create the "run" task for running a Hedera consensus node
tasks.register<JavaExec>("run") {
    group = "application"
    dependsOn(tasks.assemble)
    workingDir = nodeWorkingDir.get().asFile
    jvmArgs = listOf("-cp", "data/lib/*")
    mainClass.set("com.swirlds.platform.Browser")
}

tasks.register<JavaExec>("modrun") {
    group = "build"
    description = "Run a Hedera consensus node instance."
    dependsOn(tasks.assemble)
    workingDir = nodeWorkingDir.get().asFile
    jvmArgs = listOf("-cp", "data/lib/*:data/apps/*", "-Dhedera.workflows.enabled=true")
    mainClass.set("com.hedera.node.app.ServicesMain")
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
