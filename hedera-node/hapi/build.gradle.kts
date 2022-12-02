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
    id("com.hedera.pbj.pbj-compiler").version("0.1.0-SNAPSHOT")
}

description = "Hedera API"

configurations.all {
    exclude("javax.annotation", "javax.annotation-api")
}

dependencies {
    annotationProcessor(libs.dagger.compiler)

    implementation(libs.bundles.di)
    implementation(project(":hedera-node:hapi-utils"))
    testImplementation(testLibs.bundles.testing)
}

// Add downloaded HAPI repo protobuf files into build directory and add to sources to build them
sourceSets {
    main {
        pbj {
            srcDir(buildDir.resolve("hedera-protobufs/hapi/services"))
            srcDir(buildDir.resolve("hedera-protobufs/hapi/streams"))
        }
    }
}