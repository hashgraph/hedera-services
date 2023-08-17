/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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
    id("application")
    id("com.hedera.hashgraph.java")
    id("com.hedera.hashgraph.dependency-analysis")
}

group = "com.swirlds"

// Find the central SDK deployment dir by searching up the folder hierarchy
fun sdkDir(dir: Directory): Directory =
    if (dir.dir("sdk").asFile.exists()) dir.dir("sdk") else sdkDir(dir.dir(".."))

// Copy dependencies into `sdk/data/lib`
val copyLib =
    tasks.register<Copy>("copyLib") {
        from(project.configurations.runtimeClasspath)
        into(sdkDir(layout.projectDirectory).dir("data/lib"))
    }

// Copy built jar into `data/apps` and rename
val copyApp =
    tasks.register<Copy>("copyApp") {
        from(tasks.jar)
        into(sdkDir(layout.projectDirectory).dir("data/apps"))
        rename { "${project.name}.jar" }
    }

tasks.assemble {
    dependsOn(copyLib)
    dependsOn(copyApp)
}

// The 'application' plugin activates the following tasks as part of 'assemble'.
// As we do not use these results right now, disable them:
tasks.startScripts { enabled = false }

tasks.distTar { enabled = false }

tasks.distZip { enabled = false }

val cleanRun =
    tasks.register<Delete>("cleanRun") {
        val sdkDir = sdkDir(layout.projectDirectory)
        delete(
            sdkDir.asFileTree.matching {
                include("settingsUsed.txt")
                include("swirlds.jar")
                include("metricsDoc.tsv")
                include("*.csv")
                include("*.log")
            }
        )

        val dataDir = sdkDir.dir("data")
        delete(dataDir.dir("accountBalances"))
        delete(dataDir.dir("apps"))
        delete(dataDir.dir("lib"))
        delete(dataDir.dir("recordstreams"))
        delete(dataDir.dir("saved"))
    }

tasks.clean { dependsOn(cleanRun) }

tasks.jar {
    // Gradle fails to track 'configurations.runtimeClasspath' as an input to the task if it is
    // only used in the 'mainfest.attributes'. Hence, we explicitly add it as input.
    inputs.files(configurations.runtimeClasspath)
    manifest {
        attributes(
            "Main-Class" to application.mainClass,
            "Class-Path" to
                configurations.runtimeClasspath.get().elements.map { entry ->
                    entry
                        .map {
                            File(
                                copyLib
                                    .get()
                                    .destinationDir
                                    .relativeTo(copyApp.get().destinationDir),
                                it.asFile.name
                            )
                        }
                        .sorted()
                        .joinToString(separator = " ")
                }
        )
    }
}
