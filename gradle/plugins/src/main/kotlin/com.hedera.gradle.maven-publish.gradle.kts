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

plugins {
    id("java")
    id("maven-publish")
    id("signing")
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

        pom {
            packaging = findProperty("maven.project.packaging")?.toString() ?: "jar"
            name.set(project.name)
            url.set("https://www.swirlds.com/")
            inceptionYear.set("2016")

            description.set(provider(project::getDescription))

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

            scm {
                connection.set("scm:git:git://github.com/hashgraph/hedera-services.git")
                developerConnection.set("scm:git:ssh://github.com:hashgraph/hedera-services.git")
                url.set("https://github.com/hashgraph/hedera-services")
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
