/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.PlatformEvent;

/**
 * Utility types to define equality of events, sets of shadow events and hashes.
 */
public final class EventEquality {

    /**
     * Private ctor. This is a utility class.
     */
    private EventEquality() {
        // This ctor does nothing
    }

    /**
     * Equality of two events by hash. If the events are both null, they are considered equal.
     */
    static boolean identicalHashes(final PlatformEvent a, final PlatformEvent b) {
        return (a == null && b == null) || a.getBaseHash().equals(b.getBaseHash());
    }

    /**
     * Equality of two events by hash. If the events are both null, they are considered equal.
     */
    static boolean identicalHashes(final Hash ha, final Hash hb) {
        return (ha == null && hb == null) || ha.equals(hb);
    }
}
