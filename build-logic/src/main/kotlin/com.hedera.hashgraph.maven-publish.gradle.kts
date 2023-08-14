/*
 * Copyright 2016-2022 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Hedera Hashgraph.
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

plugins {
    java
    `maven-publish`
    signing
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
                usage("java-api") {
                    fromResolutionResult()
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                packaging = findProperty("maven.project.packaging")?.toString() ?: "jar"
                name.set(project.name)
                url.set("https://www.swirlds.com/")
                inceptionYear.set("2016")

                description.set(
                    "Swirlds is a software platform designed to build fully-distributed "
                            + "applications that harness the power of the cloud without servers. "
                            + "Now you can develop applications with fairness in decision making, "
                            + "speed, trust and reliability, at a fraction of the cost of "
                            + "traditional server-based platforms."
                )

                organization {
                    name.set("Hedera Hashgraph, LLC")
                    url.set("https://www.hedera.com")
                }

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://raw.githubusercontent.com/hashgraph/hedera-services/main/LICENSE")
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
                    developerConnection.set("scm:git:ssh://github.com:hashgraph/hedera-services.git")
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
                uri("artifactregistry://us-maven.pkg.dev/swirlds-registry/maven-develop-daily-snapshots")
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
    onlyIf {
        providers.gradleProperty("publishSigningEnabled").getOrElse("false").toBoolean()
    }
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
