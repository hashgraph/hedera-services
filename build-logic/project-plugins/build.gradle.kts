/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
    // Support convention plugins written in Kotlin. Convention plugins are build scripts in
    // 'src/main' that automatically become available as plugins in the main build.
    `kotlin-dsl`
}

dependencies {
    implementation("com.adarshr:gradle-test-logger-plugin:4.0.0")
    implementation("com.autonomousapps:dependency-analysis-gradle-plugin:1.29.0")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    implementation("com.github.johnrengelman:shadow:8.1.1")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.4")
    implementation(
        "gradle.plugin.com.google.cloud.artifactregistry:artifactregistry-gradle-plugin:2.2.1"
    )
    implementation("me.champeau.jmh:jmh-gradle-plugin:0.7.2")
    implementation("net.swiftzer.semver:semver:1.3.0")
    implementation("org.gradlex:extra-java-module-info:1.8")
    implementation("org.gradlex:java-ecosystem-capabilities:1.5.1")
    implementation("org.gradlex:java-module-dependencies:1.6.2")
    implementation("org.owasp:dependency-check-gradle:9.0.9")
}
