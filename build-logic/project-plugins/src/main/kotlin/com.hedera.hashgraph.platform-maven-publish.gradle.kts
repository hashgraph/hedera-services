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

import org.gradle.kotlin.dsl.support.serviceOf

plugins {
    id("java")
    id("com.hedera.hashgraph.maven-publish")
}

@Suppress("UnstableApiUsage")
if (!serviceOf<BuildFeatures>().configurationCache.active.get()) {
    // plugin to support 'artifactregistry' repositories that currently only works without
    // configuration cache
    // https://github.com/GoogleCloudPlatform/artifact-registry-maven-tools/issues/85
    apply(plugin = "com.google.cloud.artifactregistry.gradle-plugin")
}

publishing {
    publications {
        named<MavenPublication>("maven") {
            pom.description.set(
                "Swirlds is a software platform designed to build fully-distributed " +
                    "applications that harness the power of the cloud without servers. " +
                    "Now you can develop applications with fairness in decision making, " +
                    "speed, trust and reliability, at a fraction of the cost of " +
                    "traditional server-based platforms."
            )

            pom.developers {
                developer {
                    name.set("Platform Base Team")
                    email.set("platform-base@swirldslabs.com")
                    organization.set("Hedera Hashgraph")
                    organizationUrl.set("https://www.hedera.com")
                }
                developer {
                    name.set("Platform Hashgraph Team")
                    email.set("platform-hashgraph@swirldslabs.com")
                    organization.set("Hedera Hashgraph")
                    organizationUrl.set("https://www.hedera.com")
                }
                developer {
                    name.set("Platform Data Team")
                    email.set("platform-data@swirldslabs.com")
                    organization.set("Hedera Hashgraph")
                    organizationUrl.set("https://www.hedera.com")
                }
                developer {
                    name.set("Release Engineering Team")
                    email.set("release-engineering@swirldslabs.com")
                    organization.set("Hedera Hashgraph")
                    organizationUrl.set("https://www.hedera.com")
                }
            }

            repositories {
                maven {
                    name = "prereleaseChannel"
                    url =
                        uri(
                            "artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-prerelease-channel"
                        )
                }
                maven {
                    name = "developSnapshot"
                    url =
                        uri(
                            "artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-develop-snapshots"
                        )
                }
                maven {
                    name = "developDailySnapshot"
                    url =
                        uri(
                            "artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-develop-daily-snapshots"
                        )
                }
                maven {
                    name = "developCommit"
                    url =
                        uri(
                            "artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-develop-commits"
                        )
                }
                maven {
                    name = "adhocCommit"
                    url =
                        uri(
                            "artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-adhoc-commits"
                        )
                }
                maven {
                    name = "sonatype"
                    url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                    credentials {
                        username = System.getenv("OSSRH_USERNAME")
                        password = System.getenv("OSSRH_PASSWORD")
                    }
                }
                maven {
                    name = "sonatypeSnapshot"
                    url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                    credentials {
                        username = System.getenv("OSSRH_USERNAME")
                        password = System.getenv("OSSRH_PASSWORD")
                    }
                }
            }
        }
    }
}

tasks.register("releaseMavenCentral") {
    group = "release"
    dependsOn(tasks.named("publishMavenPublicationToSonatypeRepository"))
}

tasks.register("releaseMavenCentralSnapshot") {
    group = "release"
    dependsOn(tasks.named("publishMavenPublicationToSonatypeSnapshotRepository"))
}

tasks.register("releaseDevelopSnapshot") {
    group = "release"
    dependsOn(tasks.named("publishMavenPublicationToDevelopSnapshotRepository"))
}

tasks.register("releaseDevelopDailySnapshot") {
    group = "release"
    dependsOn(tasks.named("publishMavenPublicationToDevelopDailySnapshotRepository"))
}

tasks.register("releaseDevelopCommit") {
    group = "release"
    dependsOn(tasks.named("publishMavenPublicationToDevelopCommitRepository"))
}

tasks.register("releaseAdhocCommit") {
    group = "release"
    dependsOn(tasks.named("publishMavenPublicationToAdhocCommitRepository"))
}

tasks.register("releasePrereleaseChannel") {
    group = "release"
    dependsOn(tasks.named("publishMavenPublicationToPrereleaseChannelRepository"))
}
