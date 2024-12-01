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

package com.hedera.services.bdd.junit.restart;

/**
 * The types of network overrides that may be on disk for a {@link RestartHapiTest}.
 */
public enum NetworkOverride {
    /**
     * No network override is present.
     */
    NONE,
    /**
     * A network override with only the network roster is present.
     */
    ROSTER_ONLY,
    /**
     * A network override with both the network roster and the ledger id is present.
     */
    ROSTER_AND_LEDGER_ID,
}
