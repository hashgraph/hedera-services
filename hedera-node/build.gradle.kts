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
    id("com.hedera.hashgraph.hedera-conventions")
    id("com.hedera.hashgraph.benchmark-conventions")
}

description = "Hedera Services Node"

dependencies {
    annotationProcessor(libs.dagger.compiler)

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

    testImplementation(testLibs.bundles.testing)

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
    manifest {
        attributes(
            "Main-Class" to "com.hedera.services.ServicesMain",
            "Class-Path" to configurations.getByName("runtimeClasspath")
                .joinToString(separator = " ") { "../lib/" + it.name }
        )
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
    into(File(project.projectDir, "data/lib"))
    shouldRunAfter(tasks.assemble)
}

// Copy built jar into `data/apps` and rename HederaNode.jar
val copyApp = tasks.register<Copy>("copyApp") {
    from(tasks.jar)
    into("data/apps")
    rename { "HederaNode.jar" }
    shouldRunAfter(tasks.assemble)
}

tasks.assemble {
    dependsOn(copyApp)
    dependsOn(copyLib)
}

// Create the "run" task for running a Hedera consensus node
tasks.register<JavaExec>("run") {
    dependsOn(tasks.assemble)
    classpath("data/apps/HederaNode.jar")
}

val cleanRun = tasks.register("cleanRun") {
    project.delete(File(project.projectDir, "database"))
    project.delete(File(project.projectDir, "output"))
    project.delete(File(project.projectDir, "settingsUsed.txt"))
    project.delete(File(project.projectDir, "swirlds.jar"))
    project.projectDir.list { _, fileName -> fileName.startsWith("MainNetStats") }
        ?.forEach { file ->
            project.delete(file)
        }

    val dataDir = File(project.projectDir, "data")
    project.delete(File(dataDir, "accountBalances"))
    project.delete(File(dataDir, "apps"))
    project.delete(File(dataDir, "lib"))
    project.delete(File(dataDir, "recordstreams"))
    project.delete(File(dataDir, "saved"))
}

tasks.clean {
    dependsOn(cleanRun)
}

tasks.register("showHapiVersion") {
    doLast {
        println(versionCatalogs.named("libs").findVersion("hapi-version").get().requiredVersion)
    }
}
