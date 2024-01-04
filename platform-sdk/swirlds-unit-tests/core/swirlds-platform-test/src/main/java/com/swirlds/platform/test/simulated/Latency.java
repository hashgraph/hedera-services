/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.simulated;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * Latency for a single node in a hub-and-spoke model
 *
 * @param delay the delay of this node. The time for a message to reach a peer is the sum of this delay and the peer's
 *              delay
 */
public record Latency(@NonNull Duration delay) {

    /**
     * Returns {@code true} if this latency is equal to {@link Duration#ZERO};
     */
    public boolean isZero() {
        return delay.isZero();
    }
}
