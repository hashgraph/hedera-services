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

plugins { id("com.hedera.gradle.application") }

mainModuleInfo {
    runtimeOnly("com.swirlds.platform.core")
    runtimeOnly("com.swirlds.merkle")
    runtimeOnly("com.swirlds.merkle.test.fixtures")
}

application.mainClass.set("com.swirlds.platform.Browser")

tasks.copyApp {
    // Adjust configuration from 'com.hedera.hashgraph.application':
    // Copy directly into 'sdk' and not 'sdk/data/apps'
    into(layout.projectDirectory.dir("../sdk"))
}

tasks.jar {
    // Gradle fails to track 'configurations.runtimeClasspath' as an input to the task if it is
    // only used in the 'manifest.attributes'. Hence, we explicitly add it as input.
    inputs.files(configurations.runtimeClasspath)
    doFirst {
        manifest {
            attributes(
                "Class-Path" to
                    inputs.files
                        .filter { it.extension == "jar" }
                        .map { "data/lib/" + it.name }
                        .sorted()
                        .joinToString(separator = " ")
            )
        }
    }
}
