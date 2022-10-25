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
}

description = "Hedera Application - MONO Service Implementation"

dependencies {
    annotationProcessor(libs.dagger.compiler)

    api(project(":hedera-node:hedera-evm-api"))
    implementation(project(":hapi-fees"))
    implementation(project(":hapi-utils"))
    implementation(libs.bundles.besu) {
        exclude(group = "org.hyperledger.besu", module = "secp256r1")
    }
    implementation(libs.bundles.di)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.swirlds)
    implementation(libs.caffeine)
    implementation(libs.hapi)
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

    testImplementation(testLibs.bundles.testing)
    testImplementation(testLibs.classgraph)

    jmhImplementation(libs.swirlds.common)

    runtimeOnly(libs.bundles.netty)
}

val apt = configurations.create("apt")
dependencies {
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
                    "Main-Class" to "com.hedera.services.ServicesMain",
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
