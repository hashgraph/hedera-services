package com.swirlds.platform.event.validation;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Describes a recent event.
 *
 * @param creatorId  the ID of the event's creator
 * @param generation the event's generation
 * @param hash       the hash of the event
 */
public record RecentEvent(
        @NonNull NodeId creatorId,
        long generation,
        @NonNull Hash hash) {

    public static RecentEvent of(@NonNull final GossipEvent event) {

        final Hash hash = event.getHashedData().getHash();
        if (hash == null) {
            throw new IllegalArgumentException("event must be hashed prior to deduplication");
        }

        return new RecentEvent(
                event.getHashedData().getCreatorId(),
                event.getGeneration(),
                event.getHashedData().getHash());
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
        return this.creatorId == that.creatorId
                && this.generation == that.generation
                && this.hash.equals(that.hash);
    }
}