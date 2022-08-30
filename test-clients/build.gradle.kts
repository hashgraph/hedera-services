/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.hedera-conventions")
    id("com.hedera.hashgraph.shadow-jar")
}

description = "Hedera Services Test Clients for End to End Tests (EET)"

tasks.test {
    // Disable these EET tests from being executed as part of the gradle "test" task. We should maybe remove them
    // from src/test into src/eet, so it can be part of an eet test task instead. See issue #3412
    // (https://github.com/hashgraph/hedera-services/issues/3412).
    exclude("**/*")
}

sourceSets {
    // Needed because "resource" directory is misnamed. See https://github.com/hashgraph/hedera-services/issues/3361
    main {
        resources {
            srcDir("src/main/resource")
        }
    }
}

dependencies {
    implementation(project(":hapi-utils"))
    implementation(project(":hapi-fees"))
    implementation(libs.bundles.besu)
    implementation(libs.bundles.logging)
    implementation(testLibs.besu.internal)
    implementation(testLibs.commons.collections4)
    implementation(libs.commons.io)
    implementation(testLibs.ethereumj) {
        exclude("", "junit")
        exclude("com.cedarsoftware")
        exclude("com.googlecode.json-simple")
        exclude("io.netty")
        exclude("org.apache.logging.log4j")
        exclude("org.ethereum")
        exclude("org.iq80.leveldb")
        exclude("org.slf4j")
        exclude("org.xerial.snappy")
    }
    implementation(libs.guava)
    implementation(libs.hapi)
    implementation(libs.headlong)
    implementation(testLibs.json)
    implementation(testLibs.junit.jupiter.api)
    implementation(testLibs.picocli)
    implementation(libs.protobuf.java)
    implementation(testLibs.snakeyaml)
    implementation(libs.swirlds.common)
    implementation(testLibs.testcontainers.core)
    itestImplementation(project.parent!!.project("hedera-node"))
    itestImplementation(libs.bundles.swirlds)
    itestImplementation(testLibs.bundles.testcontainers)
    eetImplementation(testLibs.bundles.testcontainers)
}

tasks.itest {
    systemProperty("junit.jupiter.execution.parallel.enabled", false)
    systemProperty("TAG", "services-node:" + project.version)
    systemProperty("networkWorkspaceDir", File(project.buildDir, "network/itest"))
}

tasks.eet {
    systemProperty("TAG", "services-node:" + project.version)
    systemProperty("networkWorkspaceDir", File(project.buildDir, "network/eet"))
}

val sjJar: String by project
val sjMainClass: String by project
tasks.shadowJar {
    archiveFileName.set(sjJar)
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
    fileMode = 664
    dirMode = 775

    manifest {
        attributes(
            "Main-Class" to sjMainClass
        )
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}
