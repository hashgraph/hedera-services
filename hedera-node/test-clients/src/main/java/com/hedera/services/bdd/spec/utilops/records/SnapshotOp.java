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

package com.hedera.services.bdd.spec.utilops.records;

import com.hedera.services.bdd.spec.HapiSpec;

/**
 * Defines a record snapshot operation.
 */
public interface SnapshotOp {
    /**
     * Returns whether this operation has work to do, i.e., whether it could run against the target network.
     *
     * @return if this operation can run against the target network
     */
    boolean hasWorkToDo();

    /**
     * The special snapshot operation entrypoint, called by the {@link HapiSpec} when it is time to read all
     * generated record files and either snapshot or fuzzy-match their contents.
     */
    void finishLifecycle();
}
