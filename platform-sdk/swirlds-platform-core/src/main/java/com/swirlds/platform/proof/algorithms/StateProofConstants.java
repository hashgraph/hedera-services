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

package com.swirlds.platform.proof.algorithms;

import static com.swirlds.common.units.DataUnit.UNIT_BYTES;
import static com.swirlds.common.units.DataUnit.UNIT_MEGABYTES;

/**
 * Constants used by the state proof implementation.
 */
public final class StateProofConstants {

    /**
     * The maximum number of signatures supported by deserialization.
     */
    public static final int MAX_SIGNATURE_COUNT = 1024;

    /**
     * The maximum number of children a state proof node is permitted to have.
     */
    public static final int MAX_CHILD_COUNT = 64;

    /**
     * The maximum size of the state proof tree supported by deserialization, in bytes. This constant is chosen to be
     * sufficiently large as to be unlikely to be reached in practice, but small enough to prevent memory exhaustion in
     * the event of an attack.
     */
    public static final long MAX_STATE_PROOF_TREE_SIZE = (long) UNIT_MEGABYTES.convertTo(64, UNIT_BYTES);

    /**
     * The maximum size of the opaque data supported by deserialization, in bytes. This constant is chosen to be
     * sufficiently large as to be unlikely to be reached in practice, but small enough to prevent memory exhaustion in
     * the event of an attack.
     */
    public static final int MAX_OPAQUE_DATA_SIZE = (int) UNIT_MEGABYTES.convertTo(1, UNIT_BYTES);

    private StateProofConstants() {}
}
