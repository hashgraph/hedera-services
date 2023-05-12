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

package com.swirlds.common.system;

import java.time.Instant;

/**
 * An item that has reached consensus.
 * <p>
 * IMPORTANT: Although this interface is not sealed, it should only be implemented by internal classes. This
 * interface may be changed at any time, in any way, without notice or prior deprecation. Third parties should NOT
 * implement this interface.
 */
public interface ReachedConsensus {

    /**
     * Returns the consensus order of the consensus item, starting at zero. Smaller values occur before higher numbers.
     *
     * @return the consensus order sequence number
     */
    long getConsensusOrder();

    /**
     * Returns the community's consensus timestamp for this item.
     *
     * @return the consensus timestamp
     */
    Instant getConsensusTimestamp();
}
