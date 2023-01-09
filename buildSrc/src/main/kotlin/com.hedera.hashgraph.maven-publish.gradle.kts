import com.diffplug.gradle.spotless.JavaExtension
import java.time.Duration

/*
 * Copyright 2016-2023 Hedera Hashgraph, LLC
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
    `java`
    `maven-publish`
    signing
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components.getByName("java"))

            pom {
                packaging = findProperty("maven.project.packaging")?.toString() ?: "jar"
                name.set(project.name)
                description.set(provider(project::getDescription))
                url.set("https://www.hedera.com/")
                inceptionYear.set("2016")

                organization {
                    name.set("Hedera Hashgraph, LLC")
                    url.set("https://www.hedera.com")
                }

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://raw.githubusercontent.com/hashgraph/hedera-services/master/LICENSE")
                    }
                }

                developers {
                    developer {
                        name.set("Services Team")
                        email.set("hedera-services@swirldslabs.com")
                        organization.set("Hedera Hashgraph")
                        organizationUrl.set("https://www.hedera.com")
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
            name = "sonatype"
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                password = System.getenv("OSSRH_PASSWORD")
            }
        }
        maven {
            name = "sonatypeSnapshot"
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
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

tasks.register("release-maven-central") {
    group = "release"
    dependsOn(tasks.named<Task>("publishMavenPublicationToSonatypeRepository"))
}

tasks.register("release-maven-central-snapshot") {
    group = "release"
    dependsOn(tasks.named<Task>("publishMavenPublicationToSonatypeSnapshotRepository"))
}

java {
    if (tasks.getByName("release-maven-central").enabled ||
            tasks.getByName("release-maven-central-snapshot").enabled) {
        withJavadocJar()
        withSourcesJar()
    }
}
