/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.records;

/**
 * Enumerates the record stream sources that can be targeted with properties {@code recordStream.autoSnapshotTarget}
 * and {@code recordStream.autoMatchTarget} when {@code recordStream.autoSnapshotManagement=true}.
 */
public enum AutoSnapshotRecordSource {
    /**
     * The record stream source is a {@link com.hedera.services.bdd.junit.HapiTest} network.
     */
    HAPI_TEST,
}
