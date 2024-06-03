/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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
    id("com.hedera.gradle.java")
}

group = "com.swirlds"

// Module Names of 'demo' projects do not always fit the expected pattern.
// Which is ok for the independent 'demo' projects that only consist of one project.
javaModuleDependencies.moduleNameCheck.set(false)

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
        inputs.property("projectName", project.name)

        from(tasks.jar)
        into(sdkDir(layout.projectDirectory).dir("data/apps"))
        rename { "${inputs.properties["projectName"]}.jar" }
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

tasks.jar { manifest { attributes("Main-Class" to application.mainClass) } }
