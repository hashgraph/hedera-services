/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.spotless-conventions")
    id("com.hedera.hashgraph.spotless-kotlin-conventions")
}

repositories {
    mavenCentral()
}

description = "Hedera Services Node"

var removeTempDockerFilesTask = tasks.register<Delete>("removeTempDockerFiles") {
    description = "Deletes all temp docker files that are copied in the root folder to create the docker image"
    group = "docker"

    delete(
        "${rootProject.projectDir}/.env",
        "${rootProject.projectDir}/.dockerignore",
        "${rootProject.projectDir}/Dockerfile"
    )
}

tasks.clean {
    dependsOn(removeTempDockerFilesTask)
}

var updateDockerEnvTask = tasks.register<Exec>("updateDockerEnv") {
    description = "Creates the .env file in the docker folder that contains environment variables for docker"
    group = "docker"

    workingDir("$projectDir/docker")
    commandLine("./update-env.sh", project.version)
}

tasks.register<Exec>("createDockerImage") {
    description = "Creates the docker image of the services based on the current version"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir("$projectDir/docker")
    commandLine("./docker-build.sh", project.version, rootProject.projectDir)
    finalizedBy(removeTempDockerFilesTask)
}

tasks.register<Exec>("startDockerContainers") {
    description = "Starts docker containers of the services based on the current version"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir("$projectDir/docker")
    commandLine("docker-compose", "up")
}

tasks.register<Exec>("stopDockerContainers") {
    description = "Stops running docker containers of the services"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir("$projectDir/docker")
    commandLine("docker-compose", "stop")
}
