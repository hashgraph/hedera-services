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
    id("java")
    id("maven-publish")
    id("signing")
    id("com.google.cloud.artifactregistry.gradle-plugin")
}

java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components.getByName("java"))
            versionMapping {
                usage("java-api") { fromResolutionResult() }
                usage("java-runtime") { fromResolutionResult() }
            }

            pom {
                packaging = findProperty("maven.project.packaging")?.toString() ?: "jar"
                name.set(project.name)
                url.set("https://www.swirlds.com/")
                inceptionYear.set("2016")

                description.set(
                    "Swirlds is a software platform designed to build fully-distributed " +
                        "applications that harness the power of the cloud without servers. " +
                        "Now you can develop applications with fairness in decision making, " +
                        "speed, trust and reliability, at a fraction of the cost of " +
                        "traditional server-based platforms."
                )

                organization {
                    name.set("Hedera Hashgraph, LLC")
                    url.set("https://www.hedera.com")
                }

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set(
                            "https://raw.githubusercontent.com/hashgraph/hedera-services/main/LICENSE"
                        )
                    }
                }

                developers {
                    developer {
                        name.set("Services Team")
                        email.set("hedera-services@swirldslabs.com")
                        organization.set("Hedera Hashgraph")
                        organizationUrl.set("https://www.hedera.com")
                    }
                    developer {
                        name.set("Nathan Klick")
                        email.set("nathan@swirldslabs.com")
                        organization.set("Swirlds Labs, Inc.")
                        organizationUrl.set("https://www.swirldslabs.com")
                    }
                    developer {
                        name.set("Lazar Petrovic")
                        email.set("lazar@swirldslabs.com")
                        organization.set("Swirlds Labs, Inc.")
                        organizationUrl.set("https://www.swirldslabs.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/hashgraph/hedera-services.git")
                    developerConnection.set(
                        "scm:git:ssh://github.com:hashgraph/hedera-services.git"
                    )
                    url.set("https://github.com/hashgraph/hedera-services")
                }
            }
        }
    }
    repositories {
        maven {
            name = "prereleaseChannel"
            url =
                uri("artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-prerelease-channel")
        }
        maven {
            name = "developSnapshot"
            url =
                uri("artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-develop-snapshots")
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
            url = uri("artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-develop-commits")
        }
        maven {
            name = "adhocCommit"
            url = uri("artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-adhoc-commits")
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

signing {
    useGpgCmd()
    sign(publishing.publications.getByName("maven"))
}

tasks.withType<Sign>().configureEach {
    onlyIf { providers.gradleProperty("publishSigningEnabled").getOrElse("false").toBoolean() }
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

tasks.register("releaseMavenCentral") {
    group = "release"
    dependsOn(tasks.named("publishMavenPublicationToSonatypeRepository"))
}

tasks.register("releaseMavenCentralSnapshot") {
    group = "release"
    dependsOn(tasks.named("publishMavenPublicationToSonatypeSnapshotRepository"))
}
