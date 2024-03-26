/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

plugins { id("org.gradlex.java-module-dependencies") }

// The following is required as we use different Module Name prefixes. Right now we have:
// - 'com.' for 'com.swirlds' modules
// - 'com.hedera.node.' for 'com.hedera.hashgraph' modules
// - 'com.hedera.storage' for 'com.hedera.storage.blocknode' modules
// If one of the module groups has 'requires' to modules of another group, we need to register
// that module group here.
javaModuleDependencies {
    moduleNamePrefixToGroup.put("com.", "com.swirlds")
    moduleNamePrefixToGroup.put("com.hedera.node.", "com.hedera.hashgraph")
    moduleNamePrefixToGroup.put("com.hedera.storage.", "com.hedera.storage.blocknode")
}
