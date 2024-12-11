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

plugins { id("com.hedera.gradle.module.application") }

val sdkDir = layout.projectDirectory.dir("sdk")

tasks.named<JavaExec>("run") {
    workingDir = sdkDir.asFile
    mainClass = "com.swirlds.platform.Browser"
    classpath = sdkDir.asFileTree.matching { include("*.jar") }
    jvmArgs = listOf("-agentlib:jdwp=transport=dt_socket,address=8888,server=y,suspend=n")
    maxHeapSize = "8g"

    // Build everything for the 'sdk' folder
    dependsOn(":swirlds:assemble")
}

val cleanRun =
    tasks.register<Delete>("cleanRun") {
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

tasks.checkModuleDirectivesScope { this.enabled = false }
