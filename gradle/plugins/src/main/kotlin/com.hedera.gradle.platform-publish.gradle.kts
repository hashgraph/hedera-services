/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
    id("java")
    id("com.hedera.gradle.maven-publish")
}

if (
    gradle.startParameter.taskNames.any { it.startsWith("release") || it.contains("MavenCentral") }
) {
    // We apply the 'artifactregistry' plugin conditionally, as there are two issues:
    // 1. It does not support configuration cache.
    //    https://github.com/GoogleCloudPlatform/artifact-registry-maven-tools/issues/85
    // 2. It does not interact well with the 'gradle-nexus.publish-plugin' plugin, causing:
    //    'No staging repository with name sonatype created' during IDE import
    publishing.repositories.remove(publishing.repositories.getByName("sonatype"))
    apply(plugin = "com.google.cloud.artifactregistry.gradle-plugin")
}

publishing.repositories {
    maven("artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-prerelease-channel") {
        name = "prereleaseChannel"
    }
    maven("artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-develop-snapshots") {
        name = "developSnapshot"
    }
    maven("artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-develop-daily-snapshots") {
        name = "developDailySnapshot"
    }
    maven("artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-develop-commits") {
        name = "developCommit"
    }
    maven("artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-adhoc-commits") {
        name = "adhocCommit"
    }
}

// Register one 'release*' task for each publishing repository
publishing.repositories.all {
    val ucName = name.replaceFirstChar { it.titlecase() }
    tasks.register("release$ucName") {
        group = "release"
        dependsOn(tasks.named("publishMavenPublicationTo${ucName}Repository"))
    }
}
