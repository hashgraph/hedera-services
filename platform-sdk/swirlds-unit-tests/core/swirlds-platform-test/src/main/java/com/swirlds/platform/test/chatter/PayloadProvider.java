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

package com.swirlds.platform.test.chatter;

import java.time.Instant;

public interface PayloadProvider {
    /**
     * Generate a payload to send over the network.
     *
     * @param now
     * 		the current time
     * @param underutilizedNetwork
     * 		if true then the network is currently not being fully utilized
     * @param destination
     * 		the destination to generate a payload for
     * @return a payload to send, or null if no payload should be sent right now
     */
    GossipPayload generatePayload(Instant now, boolean underutilizedNetwork, long destination);
}
