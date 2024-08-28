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
    id("com.hedera.gradle.lifecycle")
    id("io.github.gradle-nexus.publish-plugin")
}

val publishingPackageGroup = providers.gradleProperty("publishingPackageGroup").getOrElse("")
val isPlatformPublish = publishingPackageGroup == "com.swirlds"

nexusPublishing {
    packageGroup = publishingPackageGroup
    repositories {
        sonatype {
            username = System.getenv("NEXUS_USERNAME")
            password = System.getenv("NEXUS_PASSWORD")
            if (isPlatformPublish) {
                nexusUrl = uri("https://s01.oss.sonatype.org/service/local/")
                snapshotRepositoryUrl =
                    uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            }
        }
    }
}

tasks.named("closeSonatypeStagingRepository") {
    // The publishing of all components to Maven Central is automatically done before close (which
    // is done before release).
    dependsOn(subprojects.map { ":${it.name}:releaseMavenCentral" })
}

tasks.named("releaseMavenCentral") {
    group = "release"
    dependsOn(tasks.closeAndReleaseStagingRepository)
}

tasks.register("releaseMavenCentralSnapshot") {
    group = "release"
    dependsOn(subprojects.map { ":${it.name}:releaseMavenCentral" })
}
