/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.iss.internal;

/**
 * The validity of this node's hash for a particular round.
 */
public enum HashValidityStatus {
    /**
     * The validity for this node's hash has not yet been determined.
     */
    UNDECIDED,
    /**
     * This node computed a hash equal to the consensus hash.
     */
    VALID,
    /**
     * This node computed a hash different from the consensus hash.
     */
    SELF_ISS,
    /**
     * There is no consensus hash, and the network will need human intervention to recover.
     */
    CATASTROPHIC_ISS,
    /**
     * We lack sufficient data to ever fully decide.
     */
    LACK_OF_DATA,
    /**
     * We Lack sufficient data to ever fully decide, but there is strong evidence of a severely fragmented
     * and unhealthy network.
     */
    CATASTROPHIC_LACK_OF_DATA
}
