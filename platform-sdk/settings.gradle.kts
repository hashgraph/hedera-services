/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

pluginManagement { includeBuild("../build-logic") }

plugins { id("com.hedera.hashgraph.settings") }

includeBuild("../hedera-dependency-versions")

include(":swirlds")

include(":swirlds-base")

include(":swirlds-logging")

include(":swirlds-common")

include(":swirlds-sign-tool")

include(":swirlds-config-api")

include(":swirlds-config-impl")

include(":swirlds-config-benchmark")

include(":swirlds-fchashmap")

include(":swirlds-fcqueue")

include(":swirlds-merkle")

include(":swirlds-merkledb", "swirlds-jasperdb")

include(":swirlds-virtualmap")

include(":swirlds-platform-gui")

include(":swirlds-platform-core")

include(":swirlds-cli")

include(":swirlds-benchmarks")

include(":swirlds-test-framework", "swirlds-unit-tests/common/swirlds-test-framework")

include(":swirlds-common-testing", "swirlds-unit-tests/common/swirlds-common-test")

include(":swirlds-platform-test", "swirlds-unit-tests/core/swirlds-platform-test")

include(":swirlds-merkle-test", "swirlds-unit-tests/structures/swirlds-merkle-test")

includeAllBuilds("platform-apps/demos")

includeAllBuilds("platform-apps/tests")

fun include(name: String, path: String) {
    include(name)
    project(name).projectDir = File(rootDir, path)
}

fun includeAllBuilds(containingFolder: String) {
    File(rootDir, containingFolder).listFiles()?.forEach { folder ->
        if (File(folder, "settings.gradle.kts").exists()) {
            includeBuild(folder.path)
        }
    }
}
