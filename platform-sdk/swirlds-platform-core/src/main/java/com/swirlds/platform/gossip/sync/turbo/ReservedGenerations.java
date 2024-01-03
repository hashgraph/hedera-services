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

package com.swirlds.platform.gossip.sync.turbo;

import com.swirlds.platform.gossip.shadowgraph.GenerationReservation;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Records generations sent to the peer, as well as a reservation for the generations sent.
 *
 * @param reservedGenerations the generations reserved for this iteration
 * @param generationsSent     the generations sent during this iteration
 */
public record ReservedGenerations(
        @NonNull GenerationReservation reservedGenerations, @NonNull Generations generationsSent) {

    /**
     * Release resources held by this object.
     */
    public void release() {
        reservedGenerations.close();
    }
}
