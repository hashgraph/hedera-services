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

import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRestartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStopContainer
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import java.time.Duration
import java.time.temporal.ChronoUnit.SECONDS

plugins {
    id("com.hedera.hashgraph.aggregate-reports")
    id("com.hedera.hashgraph.spotless-conventions")
    id("com.hedera.hashgraph.spotless-kotlin-conventions")
    id("com.bmuschko.docker-remote-api").version("9.1.0")
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
}

var removeTempDockerFilesTask =
        tasks.register<Delete>("removeTempDockerFiles") {
            description =
                    "Deletes all temp docker files that are copied in the root folder to create the docker image"
            group = "docker"

            delete(".env", ".dockerignore", "Dockerfile")
        }

tasks.clean { dependsOn(removeTempDockerFilesTask) }

var updateDockerEnvTask =
        tasks.register<Exec>("updateDockerEnv") {
            description =
                    "Creates the .env file in the docker folder that contains environment variables for docker"
            group = "docker"

            workingDir("./docker")
            commandLine("./update-env.sh", project.version)
        }

tasks.register<Exec>("createDockerImage") {
    description = "Creates the docker image of the services based on the current version"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir("./docker")
    commandLine("./docker-build.sh", project.version)
    finalizedBy(removeTempDockerFilesTask)
}

tasks.register<Exec>("startDockerContainers") {
    description = "Starts docker containers of the services based on the current version"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir("./docker")
    commandLine("docker-compose", "up")
}

tasks.register<Exec>("stopDockerContainers") {
    description = "Stops running docker containers of the services"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir("./docker")
    commandLine("docker-compose", "stop")
}

var buildPrometheusImageTask =
        tasks.register<DockerBuildImage>("buildPrometheusImage") {
            description = "Builds Prometheus Docker image"
            group = "docker intern"

            inputDir.set(file("docker/prometheus"))
            images.add("swirlds/platform-prometheus:latest")
        }

var buildGrafanaImageTask =
        tasks.register<DockerBuildImage>("buildGrafanaImage") {
            description = "Builds Grafana Docker image"
            group = "docker intern"

            inputDir.set(file("docker/grafana"))
            images.add("swirlds/services-grafana:latest")
        }

var createPrometheusContainerTask =
        tasks.register<DockerCreateContainer>("createPrometheusContainer") {
            description = "Create Prometheus Docker container"
            group = "docker intern"
            dependsOn(buildPrometheusImageTask)

            targetImageId(buildPrometheusImageTask.get().imageId)
            hostConfig.portBindings.set(listOf("9090:9090"))
            hostConfig.autoRemove.set(true)
        }

var createGrafanaContainerTask =
        tasks.register<DockerCreateContainer>("createGrafanaContainer") {
            description = "Create Grafana Docker container"
            group = "docker intern"
            dependsOn(buildGrafanaImageTask)

            targetImageId(buildGrafanaImageTask.get().imageId)
            hostConfig.portBindings.set(listOf("3000:3000"))
            hostConfig.autoRemove.set(true)
        }

var stopPrometheusContainer =
        tasks.register<DockerStopContainer>("stopPrometheusContainer") {
            description = "Stop Prometheus Docker container"
            group = "docker"
            dependsOn(createPrometheusContainerTask)

            targetContainerId(createPrometheusContainerTask.get().containerId)

            onError {
                if (this.message!!.contains("Status 304")) {
                    throw java.lang.IllegalStateException(
                            "Container with id ${createPrometheusContainerTask.get().containerId.get()} does not exist",
                            this)
                }
                throw this
            }
        }

tasks.register<DockerRestartContainer>("startPrometheusContainer") {
    description = "Start Prometheus Docker container"
    group = "docker"
    dependsOn(createPrometheusContainerTask)

    timeout.set(Duration.of(5, SECONDS))
    targetContainerId(createPrometheusContainerTask.get().containerId)
}

tasks.register<DockerLogsContainer>("showPrometheusContainerLog") {
    description = "Show Prometheus Docker container log"
    group = "docker intern"
    dependsOn(createPrometheusContainerTask)
    targetContainerId(createPrometheusContainerTask.get().containerId)
    follow.set(true)
}


tasks.register<DockerRestartContainer>("startGrafanaContainer") {
    description = "Start Grafana Docker container"
    group = "docker"
    dependsOn(createGrafanaContainerTask)

    timeout.set(Duration.of(5, SECONDS))
    targetContainerId(createGrafanaContainerTask.get().containerId)
}

var stopGrafanaContainer =
        tasks.register<DockerStopContainer>("stopGrafanaContainer") {
            description = "Stop Grafana Docker container"
            group = "docker"
            dependsOn(createGrafanaContainerTask)

            targetContainerId(createGrafanaContainerTask.get().containerId)

            onError {
                if (this.message!!.contains("Status 304")) {
                    throw java.lang.IllegalStateException(
                            "Container with id ${createGrafanaContainerTask.get().containerId.get()} does not exist",
                            this)
                }
                throw this
            }
        }

tasks.register<DockerLogsContainer>("showGrafanaContainerLog") {
    description = "Show Grafana Docker container log"
    group = "docker intern"
    dependsOn(createGrafanaContainerTask)
    targetContainerId(createGrafanaContainerTask.get().containerId)
    follow.set(true)
}