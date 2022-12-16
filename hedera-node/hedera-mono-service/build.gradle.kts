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
plugins {
    id("com.hedera.hashgraph.conventions")
    id("com.hedera.hashgraph.benchmark-conventions")
    `java-test-fixtures`
}

description = "Hedera Application - MONO Service Implementation"

configurations.all {
    exclude("javax.annotation", "javax.annotation-api")
    exclude("com.google.code.findbugs", "jsr305")
    exclude("org.jetbrains", "annotations")
    exclude("org.checkerframework", "checker-qual")

    exclude("io.grpc", "grpc-core")
    exclude("io.grpc", "grpc-context")
    exclude("io.grpc", "grpc-api")
    exclude("io.grpc", "grpc-testing")

    exclude("org.hamcrest", "hamcrest-core")
}

dependencies {
    annotationProcessor(libs.dagger.compiler)

    api(project(":hedera-node:hedera-evm"))
    api(project(":hedera-node:hedera-app-spi"))
    api(project(":hedera-node:hedera-admin-service"))
    api(project(":hedera-node:hedera-consensus-service"))
    api(project(":hedera-node:hedera-file-service"))
    api(project(":hedera-node:hedera-network-service"))
    api(project(":hedera-node:hedera-schedule-service"))
    api(project(":hedera-node:hedera-smart-contract-service"))
    api(project(":hedera-node:hedera-token-service"))
    api(project(":hedera-node:hedera-util-service"))

    implementation(project(":hedera-node:hapi-fees"))
    implementation(project(":hedera-node:hapi-utils"))

    implementation(libs.bundles.besu) {
        exclude(group = "org.hyperledger.besu", module = "secp256r1")
    }
    implementation(libs.bundles.di)
    implementation(libs.grpc.stub)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.swirlds)
    implementation(libs.caffeine)
    implementation(libs.hapi)
    implementation(libs.helidon.io.grpc)
    implementation(libs.headlong)
    implementation(
        variantOf(libs.netty.transport.native.epoll) {
            classifier("linux-x86_64")
        }
    )
    implementation(libs.commons.codec)
    implementation(libs.commons.io)
    implementation(libs.commons.collections4)
    implementation(libs.eclipse.collections)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(testLibs.bundles.testing)
    testImplementation(testLibs.classgraph)
    testCompileOnly(libs.spotbugs.annotations)

    testFixturesApi(project(":hedera-node:hapi-utils"))
    testFixturesApi(libs.swirlds.merkle)
    testFixturesApi(libs.swirlds.virtualmap)
    testFixturesApi(libs.hapi)
    testFixturesApi(libs.commons.codec)
    testFixturesImplementation(testLibs.bundles.testing)

    jmhImplementation(libs.swirlds.common)

    runtimeOnly(libs.bundles.netty)
}

val apt = configurations.create("apt")
dependencies {
    testImplementation("org.jetbrains:annotations:20.1.0")
    @Suppress("UnstableApiUsage")
    apt(libs.dagger.compiler)
}

tasks.withType<JavaCompile> {
    options.annotationProcessorPath = apt
}

val jmhDaggerSources = file("build/generated/sources/annotationProcessor/java/jmh")
java.sourceSets["jmh"].java.srcDir(jmhDaggerSources)

// Add all the libs dependencies into the jar manifest!
tasks.jar {
    doFirst {
        tasks.jar.configure {
            manifest {
                attributes(
                    "Main-Class" to "com.hedera.node.app.service.mono.ServicesMain",
                    "Class-Path" to configurations.getByName("runtimeClasspath")
                        .joinToString(separator = " ") { "../../data/lib/" + it.name }

                )
            }
        }
    }
}

// Replace variables in semantic-version.properties with build variables
tasks.processResources {
    filesMatching("semantic-version.properties") {
        filter { line: String ->
            if (line.contains("hapi-proto.version")) {
                "hapi.proto.version=" + libs.versions.hapi.version.get()
            } else if (line.contains("project.version")) {
                "hedera.services.version=" + project.version
            } else {
                line
            }
        }
    }
}

// Copy dependencies into `data/lib`
val copyLib = tasks.register<Copy>("copyLib") {
    from(project.configurations.getByName("runtimeClasspath"))
    into(project(":hedera-node").file("data/lib"))
}

// Copy built jar into `data/apps` and rename HederaNode.jar
val copyApp = tasks.register<Copy>("copyApp") {
    from(tasks.jar)
    into(project(":hedera-node").file("data/apps"))
    rename { "HederaNode.jar" }
    shouldRunAfter(tasks.getByName("copyLib"))
}

tasks.assemble {
    dependsOn(copyLib)
    dependsOn(copyApp)
}

// Create the "run" task for running a Hedera consensus node
tasks.register<JavaExec>("run") {
    group = "application"
    dependsOn(tasks.assemble)
    workingDir = project(":hedera-node").projectDir
    jvmArgs = listOf("-cp", "data/lib/*")
    mainClass.set("com.swirlds.platform.Browser")
}

val cleanRun = tasks.register("cleanRun") {
    val prj = project(":hedera-node")
    prj.delete(File(prj.projectDir, "database"))
    prj.delete(File(prj.projectDir, "output"))
    prj.delete(File(prj.projectDir, "settingsUsed.txt"))
    prj.delete(File(prj.projectDir, "swirlds.jar"))
    prj.projectDir.list { _, fileName -> fileName.startsWith("MainNetStats") }
        ?.forEach { file ->
            prj.delete(file)
        }

    val dataDir = File(prj.projectDir, "data")
    prj.delete(File(dataDir, "accountBalances"))
    prj.delete(File(dataDir, "apps"))
    prj.delete(File(dataDir, "lib"))
    prj.delete(File(dataDir, "recordstreams"))
    prj.delete(File(dataDir, "saved"))
}

tasks.clean {
    dependsOn(cleanRun)
}

tasks.register("showHapiVersion") {
    doLast {
        println(versionCatalogs.named("libs").findVersion("hapi-version").get().requiredVersion)
    }
}
