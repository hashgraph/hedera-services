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

package com.swirlds.platform.sync;

import com.swirlds.common.AutoCloseableNonThrowing;

/**
 * Represents zero or more reservations for a generation. It is used to determine when it is safe to expire events in
 * a given generation. Reservations are made by gossip threads inside {@link ShadowGraph}. Generations that
 * have at least one reservation may not have any of its events expired. Implementations must decrement the number of
 * reservations on closing and must be safe for multiple threads to use simultaneously.
 */
public interface GenerationReservation extends AutoCloseableNonThrowing {

    /**
     * Returns the generation this instance tracks reservations for. The returned value is always zero or greater.
     *
     * @return the generation number
     */
    long getGeneration();

    /**
     * Returns the number of reservations for this generation at the time of invocation.
     *
     * @return number of reservations
     */
    int getNumReservations();
}
