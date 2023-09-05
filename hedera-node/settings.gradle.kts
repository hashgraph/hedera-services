/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import me.champeau.gradle.igp.gitRepositories

pluginManagement { @Suppress("UnstableApiUsage") includeBuild("../build-logic") }

plugins { id("com.hedera.hashgraph.settings") }

includeBuild("../hedera-dependency-versions")

includeBuild("../platform-sdk")

include(":app", "hedera-app")

include(":app-hapi-fees", "hapi-fees")

include(":app-hapi-utils", "hapi-utils")

include(":app-service-consensus", "hedera-consensus-service")

include(":app-service-consensus-impl", "hedera-consensus-service-impl")

include(":app-service-contract", "hedera-smart-contract-service")

include(":app-service-contract-impl", "hedera-smart-contract-service-impl")

include(":app-service-evm", "hedera-evm")

include(":app-service-evm-impl", "hedera-evm-impl")

include(":app-service-file", "hedera-file-service")

include(":app-service-file-impl", "hedera-file-service-impl")

include(":app-service-mono", "hedera-mono-service")

include(":app-service-network-admin", "hedera-network-admin-service")

include(":app-service-network-admin-impl", "hedera-network-admin-service-impl")

include(":app-service-schedule", "hedera-schedule-service")

include(":app-service-schedule-impl", "hedera-schedule-service-impl")

include(":app-service-token", "hedera-token-service")

include(":app-service-token-impl", "hedera-token-service-impl")

include(":app-service-util", "hedera-util-service")

include(":app-service-util-impl", "hedera-util-service-impl")

include(":app-spi", "hedera-app-spi")

include(":config", "hedera-config")

include(":hapi", "hapi")

include(":services-cli", "cli-clients")

include(":test-clients", "test-clients")

fun include(name: String, path: String) {
    include(name)
    project(name).projectDir = File(rootDir, path)
}

// The HAPI API version to use for Protobuf sources. This can be a tag or branch
// name from the hedera-protobufs GIT repo.
val hapiProtoVersion = "0.40.0-blocks-state-SNAPSHOT"
val hapiProtoBranchOrTag = "add-pbj-types-for-state" // hapiProtoVersion

gitRepositories {
    checkoutsDirectory.set(File(rootDir, "hapi"))
    // check branch in repo for updates every second
    refreshIntervalMillis.set(1000)
    include("hedera-protobufs") {
        uri.set("https://github.com/hashgraph/hedera-protobufs.git")
        // HAPI repo version
        tag.set(hapiProtoBranchOrTag)
        // do not load project from repo
        autoInclude.set(false)
    }
}

// Define the library catalogs available for projects to make use of
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    versionCatalogs {
        // The libs of this catalog are the **ONLY** ones that are authorized to be part of the
        // runtime distribution. These libs can be depended on during compilation, or bundled as
        // part of runtime.
        create("libs") {
            version("google-proto", "3.19.4")
            version("grpc-proto", "1.45.1")
            version("hapi-proto", hapiProtoVersion)

            plugin("pbj", "com.hedera.pbj.pbj-compiler").version("0.7.4")
        }
    }
}
