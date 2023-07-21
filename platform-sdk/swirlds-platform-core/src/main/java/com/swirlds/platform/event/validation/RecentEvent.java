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

package com.swirlds.platform.event.validation;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;

/**
 * Describes a recent event.
 *
 * @param creatorId  the ID of the event's creator
 * @param generation the event's generation
 * @param hash       the hash of the event
 * @param signature  the signature of the event (we have to check this when doing deduplication if we want to
 *                   deduplicate prior to signature validation)
 */
public record RecentEvent(@NonNull NodeId creatorId, long generation, @NonNull Hash hash, @NonNull byte[] signature) {

    public static RecentEvent of(@NonNull final GossipEvent event) {

        final Hash hash = event.getHashedData().getHash();
        if (hash == null) {
            throw new IllegalArgumentException("event must be hashed prior to deduplication");
        }

        return new RecentEvent(
                event.getHashedData().getCreatorId(),
                event.getGeneration(),
                event.getHashedData().getHash(),
                event.getUnhashedData().getSignature());
    }

    @Override
    public int hashCode() {
        return NonCryptographicHashing.hash32(creatorId.id(), generation);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != RecentEvent.class) {
            return false;
        }

        final RecentEvent that = (RecentEvent) obj;
        return this.creatorId.equals(that.creatorId)
                && this.generation == that.generation
                && this.hash.equals(that.hash)
                && Arrays.equals(this.signature, that.signature);
    }
}
