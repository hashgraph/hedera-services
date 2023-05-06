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
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
public record EventFingerprint(long creator, long generation, @NonNull Hash hash, @NonNull Instant creationTime) {

    // TODO test
    // TODO sort out EventImpl vs GossipEvent

    /**
     * Get the fingerprint of an event.
     *
     * @param event the event
     * @return the fingerprint
     */
    @NonNull
    public static EventFingerprint of(@NonNull final EventImpl event) {
        return new EventFingerprint(
                event.getCreatorId(), event.getGeneration(), event.getHash(), event.getTimeCreated());
    }

    /**
     * Get the fingerprint of an event.
     * @param event the event
     * @return the fingerprint
     */
    @NonNull
    public static EventFingerprint of(@NonNull final GossipEvent event) {
        return new EventFingerprint(
                event.getHashedData().getCreatorId(),
                event.getGeneration(),
                event.getHashedData().getHash(),
                event.getHashedData().getTimeCreated());
    }

    /**
     * Get the fingerprints of an event's parents.
     */
    @NonNull
    public static List<EventFingerprint> getParentFingerprints(@NonNull final EventImpl event) {
        final List<EventFingerprint> parentFingerprints = new ArrayList<>(2);
        if (event.getSelfParent() != null) {
            parentFingerprints.add(EventFingerprint.of(event.getSelfParent()));
        }
        if (event.getOtherParent() != null) {
            parentFingerprints.add(EventFingerprint.of(event.getOtherParent()));
        }
        return parentFingerprints;
    }

    /**
     * Get the fingerprints of an event's parents.
     */
    @NonNull
    public static List<EventFingerprint> getParentFingerprints(@NonNull final GossipEvent event) {
        final List<EventFingerprint> parentFingerprints = new ArrayList<>(2);

        if (event.getHashedData().getSelfParentHash() != null) {
            final EventFingerprint selfParentFingerprint = new EventFingerprint(
                    event.getHashedData().getCreatorId(),
                    event.getHashedData().getSelfParentGen(),
                    event.getHashedData().getSelfParentHash(),
                    Instant.EPOCH); // TODO how to figure out the correct time?
            parentFingerprints.add(selfParentFingerprint);
        }
        if (event.getHashedData().getOtherParentHash() != null) {
            final EventFingerprint otherParentFingerprint = new EventFingerprint(
                    event.getUnhashedData().getOtherId(), // TODO why is this unhashed?!
                    event.getHashedData().getOtherParentGen(),
                    event.getHashedData().getOtherParentHash(),
                    Instant.EPOCH); // TODO how to figure out the correct time?
            parentFingerprints.add(otherParentFingerprint);
        }

        return parentFingerprints;
    }

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
