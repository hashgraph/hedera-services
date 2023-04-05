/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.dispatch.triggers.flow;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.dispatch.types.TriggerFour;

/**
 * Sends dispatches when the validity of a node's reported state hash has been determined.
 * Not sent for rounds that have a catastrophic ISS (these rounds don't have a consensus hash,
 * so there is no such thing as a valid hash for that round).
 */
@FunctionalInterface
public interface StateHashValidityTrigger extends TriggerFour<Long, Long, Hash, Hash> {

    /**
     * Signal that the validity of a reported state hash can be determined
     *
     * @param round
     * 		the round number
     * @param nodeId
     * 		the ID of the node that submitted the hash
     * @param nodeHash
     * 		the hash computed by the node
     * @param consensusHash
     * 		the consensus hash computed by the network
     */
    @Override
    void dispatch(Long round, Long nodeId, Hash nodeHash, Hash consensusHash);
}
