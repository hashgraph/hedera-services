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

package com.swirlds.platform.test.simulated;

import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import java.time.Duration;
import java.util.HashMap;
import java.util.Random;

/**
 * Generates random latencies between nodes in the provided address book
 */
public class Latency {
    private final HashMap<Long, Duration> delays;

    /**
     * Create random latency mappings
     *
     * @param addressBook
     * 		the address book with all the nodes in the network
     * @param maxDelay
     * 		the maximum delay between 2 nodes
     * @param random
     * 		source of randomness
     */
    public Latency(final AddressBook addressBook, final Duration maxDelay, final Random random) {
        delays = new HashMap<>();
        for (final Address address : addressBook) {
            delays.put(address.getId(), Duration.ofMillis(random.nextInt((int)
                    maxDelay.dividedBy(2).toMillis())));
        }
    }

    /**
     * @param from
     * 		the ID of the first node
     * @param to
     * 		the ID of the second node
     * @return the delay between the two nodes
     */
    public Duration getLatency(final long from, final long to) {
        return delays.get(from).plus(delays.get(to));
    }
}
