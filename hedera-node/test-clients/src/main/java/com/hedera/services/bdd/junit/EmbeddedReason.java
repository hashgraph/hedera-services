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

/**
 * Enumerates reasons a {@link EmbeddedHapiTest} is marked as such.
 */
public enum EmbeddedReason {
    /**
     * The test must skip the ingest workflow to submit its transactions, as they would always be rejected by
     * a node in normal operations.
     */
    MUST_SKIP_INGEST,
    /**
     * The test must directly access state to assert expectations that cannot be verified through the gRPC API.
     */
    NEEDS_STATE_ACCESS,
    /**
     * The test manipulates the software version of the simulated consensus event for a transaction.
     */
    MANIPULATES_EVENT_VERSION,
}
