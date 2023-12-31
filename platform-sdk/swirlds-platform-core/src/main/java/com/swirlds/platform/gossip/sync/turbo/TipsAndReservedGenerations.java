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

package com.swirlds.platform.gossip.sync.turbo;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.gossip.shadowgraph.GenerationReservation;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Records information tips and generations sent to the peer. Also carries a reservation for the generations sent.
 *
 * @param reservedGenerations the generations reserved for this iteration
 * @param generationsSent     the generations sent during this iteration
 * @param tipsSent            the tips sent during this iteration. In iteration N+1, the peer will send back a boolean
 *                            value for each tip indicating whether it has the tip or not.
 */
public record TipsAndReservedGenerations(
        @NonNull GenerationReservation reservedGenerations,
        @NonNull Generations generationsSent,
        @NonNull List<Hash> tipsSent) {

    /**
     * Release resources held by this object.
     */
    public void release() {
        reservedGenerations.close();
    }
}
