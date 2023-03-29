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

package com.swirlds.platform.event.tipset;

import com.swirlds.common.crypto.Hash;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Uniquely identifies an event and stores basic metadata bout it.
 *
 * @param creator
 * 		the ID of the node that created this event
 * @param generation
 * 		the generation of the event
 * @param hash
 * 		the hash of the event, expected to be unique for all events
 */
public record EventFingerprint(long creator, long generation, @NonNull Hash hash) {

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        final EventFingerprint that = (EventFingerprint) obj;
        return this.hash.equals(that.hash);
    }
}
