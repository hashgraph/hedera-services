/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.google.protobuf.gradle.id

plugins {
    id("com.hedera.hashgraph.conventions")
    id("com.google.protobuf")
}

// Configure Protobuf Plugin to download protoc executable rather than using local installed version
protobuf {
    val libs = the<VersionCatalogsExtension>().named("libs")
    protoc {
        artifact = "com.google.protobuf:protoc:" + libs.findVersion("com.google.protobuf").get()
    }
    plugins {
        // Add GRPC plugin as we need to generate GRPC services
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:" + libs.findVersion("grpc.protobuf").get()
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach { it.plugins { id("grpc") } }
    }
}
