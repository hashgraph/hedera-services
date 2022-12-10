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
    `java-test-fixtures`
}

description = "Hedera Application - SPI"

configurations.all {
    exclude("javax.annotation", "javax.annotation-api")
    exclude("com.google.code.findbugs", "jsr305")
    exclude("org.jetbrains", "annotations")
    exclude("org.checkerframework", "checker-qual")
}

dependencies {
    api(libs.hapi)
    compileOnly(libs.spotbugs.annotations)
    implementation(libs.swirlds.common)
    implementation(libs.swirlds.merkle)
    implementation(libs.swirlds.virtualmap)
    implementation(libs.swirlds.jasperdb)

    testImplementation(testLibs.bundles.testing)
    testCompileOnly(libs.spotbugs.annotations)

    testFixturesCompileOnly(libs.spotbugs.annotations)
}
