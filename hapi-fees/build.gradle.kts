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

dependencies {
    implementation(project(":hapi-utils"))
    implementation(libs.bundles.logging)
    implementation(libs.commons.lang3)
    implementation(libs.hapi) {
        exclude("javax.annotation", "javax.annotation-api")
    }
    implementation(libs.javax.inject)
    implementation(libs.jackson)
    implementation(libs.jetbrains.annotation)
    testImplementation(testLibs.bundles.testing)
}
