/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.conventions")
}

description = "Hedera Services API Fees"

configurations.all {
    exclude("javax.annotation", "javax.annotation-api")
    exclude("com.google.code.findbugs", "jsr305")
    exclude("org.jetbrains", "annotations")
    exclude("org.checkerframework", "checker-qual")
}

dependencies {
    annotationProcessor(libs.dagger.compiler)

    implementation(libs.slf4j.api)

    implementation(libs.bundles.di)
    implementation(project(":hedera-node:hapi-utils"))
    implementation(libs.commons.lang3)
    implementation(libs.hapi)
    implementation(libs.jackson)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(testLibs.bundles.testing)
    testImplementation(libs.slf4j.simple)
}
