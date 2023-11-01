/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("com.github.johnrengelman.shadow")
}

tasks.withType<ShadowJar>().configureEach {
    group = "shadow"
    from(sourceSets.main.get().output)
    mergeServiceFiles()

    // Defer the resolution  of 'runtimeClasspath'. This is an issue in the shadow
    // plugin that it automatically accesses the files in 'runtimeClasspath' while
    // Gradle is building the task graph. The three lines below work around that.
    // See: https://github.com/johnrengelman/shadow/issues/882
    inputs.files(project.configurations.runtimeClasspath)
    configurations = emptyList()
    doFirst { configurations = listOf(project.configurations.runtimeClasspath.get()) }
}
