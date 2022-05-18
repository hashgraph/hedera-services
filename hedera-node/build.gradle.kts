/*-
 * ‌
 * Hedera Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

plugins {
    id("com.hedera.hashgraph.hedera-conventions")
}

description = "Hedera Services Node"

dependencies {
    annotationProcessor(libs.dagger.compiler)

    implementation(project(":hapi-fees"))
    implementation(project(":hapi-utils"))
    implementation(libs.bundles.besu)
    implementation(libs.bundles.di)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.swirlds)
    implementation(libs.caffeine)
    implementation(libs.hapi)
    implementation(libs.headlong)
    implementation(variantOf(libs.netty.transport.native.epoll) {
        classifier("linux-x86_64")
    })

    testImplementation(testLibs.bundles.testing)

    runtimeOnly(libs.bundles.netty)
}

// Add all the libs dependencies into the jar manifest!
tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.hedera.services.ServicesMain",
            "Class-Path" to configurations.getByName("runtimeClasspath")
                .map { it -> "../lib/" + it.name }
                .joinToString(separator=" ")
        )
    }
}

// Copy dependencies into `data/lib`
tasks.register<Copy>("copyLib") {
    from(project.configurations.getByName("runtimeClasspath"))
    into(File(project.projectDir, "data/lib"))
}

// Copy built jar into `data/apps` and rename HederaNode.jar
tasks.register<Copy>("copyApp") {
    from(tasks.jar)
    into("data/apps")
    rename { filename: String -> "HederaNode.jar" }
}

tasks.register<JavaExec>("run") {
    dependsOn(tasks.findByName("copyApp"), tasks.findByName("copyLib"))
    classpath("data/apps/HederaNode.jar")
}
