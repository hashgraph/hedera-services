/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit;

public enum ContextRequirement {
    /**
     * The test expects a predictable relationship between its created entity numbers,
     * meaning that it will fail if other tests are concurrently creating entities.
     */
    NO_CONCURRENT_CREATIONS,
    /**
     * The test requires changes to the network properties, which might break other
     * concurrent tests if they expect the default properties.
     */
    PROPERTY_OVERRIDES
}
