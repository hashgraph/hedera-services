/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import java.util.Properties

plugins {
    id("java")
    id("maven-publish")
    id("signing")
    id("io.freefair.maven-central.validate-poms")
}

tasks.withType<PublishToMavenRepository>().configureEach {
    // Publishing tasks are only enabled if we publish to the matching group.
    // Otherwise, Nexus configuration and credentials do not fit.
    val publishingPackageGroup = providers.gradleProperty("publishingPackageGroup").orNull
    enabled = publishingPackageGroup == project.group
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<Jar>().configureEach { setGroup(null) }

tasks.named("releaseMavenCentral") {
    group = "release"
    dependsOn(tasks.named("publishToSonatype"))
}

val maven =
    publishing.publications.create<MavenPublication>("maven") {
        from(components["java"])
        versionMapping {
            // Everything published takes the versions from the resolution result.
            // These are the versions we define in 'hedera-dependency-versions'
            // and use consistently in all modules.
            allVariants { fromResolutionResult() }
        }

        suppressAllPomMetadataWarnings()

        pom {
            val devGroups = Properties()
            val developerProperties = layout.projectDirectory.file("../developers.properties")
            devGroups.load(
                providers
                    .fileContents(developerProperties)
                    .asText
                    .orElse(
                        provider {
                            throw RuntimeException("${developerProperties.asFile} does not exist")
                        }
                    )
                    .get()
                    .reader()
            )

            name.set(project.name)
            url = "https://www.hashgraph.com/"
            inceptionYear = "2016"

            // this field must be present. Default to empty string.
            description =
                providers
                    .fileContents(layout.projectDirectory.file("../description.txt"))
                    .asText
                    .orElse(provider(project::getDescription))
                    .map { it.replace("\n", " ").trim() }
                    .orElse("")

            organization {
                name = "Hedera Hashgraph, LLC"
                url = "https://www.hedera.com"
            }

            val repoName = isolated.rootProject.name

            issueManagement {
                system = "GitHub"
                url = "https://github.com/hashgraph/$repoName/issues"
            }

            licenses {
                license {
                    name = "Apache License, Version 2.0"
                    url = "https://raw.githubusercontent.com/hashgraph/$repoName/main/LICENSE"
                }
            }

            scm {
                connection = "scm:git:git://github.com/hashgraph/$repoName.git"
                developerConnection = "scm:git:ssh://github.com:hashgraph/$repoName.git"
                url = "https://github.com/hashgraph/$repoName"
            }

            developers {
                devGroups.forEach { mail, team ->
                    developer {
                        id = team as String
                        name = team as String
                        email = mail as String
                        organization = "Hedera Hashgraph"
                        organizationUrl = "https://www.hedera.com"
                    }
                }
            }
        }
    }

val publishSigningEnabled =
    providers.gradleProperty("publishSigningEnabled").getOrElse("false").toBoolean()

if (publishSigningEnabled) {
    signing {
        sign(maven)
        useGpgCmd()
    }
}

tasks.named("qualityGate") { dependsOn(tasks.validatePomFiles) }
