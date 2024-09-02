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

pluginManagement { includeBuild("gradle/plugins") }

plugins { id("com.hedera.gradle.settings") }


javaModules {
    // Project to aggregate code coverage data for the whole repository into one report´
    module("gradle/reports")

    // This "intermediate parent project" should be removed
    module("platform-sdk") { artifact = "swirlds-platform" }

    // The Hedera API module
    module("hapi") {
        group = "com.hedera.hashgraph"
    }

    // The Hedera Cryptography modules
    directory("hedera-cryptography") {
        group = "com.hedera.cryptography"
    }

    // The Hedera platform modules
    directory("platform-sdk") {
        group = "com.swirlds"
        module("swirlds") // not actually a Module as it has no module-info.java
        module("swirlds-benchmarks") // not actually a Module as it has no module-info.java
        module("swirlds-unit-tests/core/swirlds-platform-test") // nested module is not found automatically
    }

    // The Hedera services modules
    directory("hedera-node") {
        group = "com.hedera.hashgraph"

        // Configure 'artifact' for projects where the folder does not correspond to the artifact name
        module("hapi-fees") { artifact = "app-hapi-fees" }
        module("hapi-utils") { artifact = "app-hapi-utils" }
        module("hedera-addressbook-service") { artifact = "app-service-addressbook" }
        module("hedera-addressbook-service-impl") { artifact = "app-service-addressbook-impl" }
        module("hedera-app") { artifact = "app" }
        module("hedera-app-spi") { artifact = "app-spi" }
        module("hedera-config") { artifact = "config" }
        module("hedera-consensus-service") { artifact = "app-service-consensus" }
        module("hedera-consensus-service-impl") { artifact = "app-service-consensus-impl" }
        module("hedera-file-service") { artifact = "app-service-file" }
        module("hedera-file-service-impl") { artifact = "app-service-file-impl" }
        module("hedera-network-admin-service") { artifact = "app-service-network-admin" }
        module("hedera-network-admin-service-impl") { artifact = "app-service-network-admin-impl" }
        module("hedera-schedule-service") { artifact = "app-service-schedule" }
        module("hedera-schedule-service-impl") { artifact = "app-service-schedule-impl" }
        module("hedera-smart-contract-service") { artifact = "app-service-contract" }
        module("hedera-smart-contract-service-impl") { artifact = "app-service-contract-impl" }
        module("hedera-token-service") { artifact = "app-service-token" }
        module("hedera-token-service-impl") { artifact = "app-service-token-impl" }
        module("hedera-util-service") { artifact = "app-service-util" }
        module("hedera-util-service-impl") { artifact = "app-service-util-impl" }
    }

    // Platform-base demo applications
    directory("example-apps") {
        group = "com.swirlds"
    }

    // Platform demo applications
    directory("platform-sdk/platform-apps/demos") {
        group = "com.swirlds"
    }

    // Platform test applications
    directory("platform-sdk/platform-apps/tests") {
        group = "com.swirlds"
    }

    // "BOM" with versions of 3rd party dependencies
    versions("hedera-dependency-versions")
}

// The HAPI API version to use for Protobuf sources.
val hapiProtoVersion = "0.54.0"

dependencyResolutionManagement {
    // Protobuf tool versions
    versionCatalogs.create("libs") {
        version("google-proto", "3.19.4")
        version("grpc-proto", "1.45.1")
        version("hapi-proto", hapiProtoVersion)

        plugin("pbj", "com.hedera.pbj.pbj-compiler").version("0.9.2")
    }
}
