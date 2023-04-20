/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.Locks;
import com.swirlds.common.threading.locks.locked.Locked;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * A thread safe map-like object for storing a number of states. This object automatically manages reservations.
 */
public class SignedStateMap {

    private final SortedMap<Long, ReservedSignedState> map = new TreeMap<>();
    private final AutoClosableLock lock = Locks.createAutoLock();

    /**
     * The round returned if there are no states in this map.
     */
    public static final long NO_STATE_ROUND = -1;

    /**
     * Create a new map for signed states.
     */
    public SignedStateMap() {
        // Empty Constructor.
    }

    /**
     * Get a signed state. A reservation is taken on the state before this method returns.
     *
     * @param round  the round to get
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @return an auto-closable object that wraps a signed state. May point to a null state if there is no state for the
     * given round. Will automatically release the state when closed.
     */
    public @NonNull ReservedSignedState getAndReserve(final long round, @NonNull final String reason) {
        try (final Locked l = lock.lock()) {
            final ReservedSignedState reservedSignedState = map.get(round);
            if (reservedSignedState == null) {
                return new ReservedSignedState();
            }
            return reservedSignedState.getAndReserve(reason);
        }
    }

    /**
     * Get the latest state in this map. A reservation is taken on the state before this method returns.
     *
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @return an auto-closable object that wraps a signed state. May point to a null state if there is no state for the
     * given round. Will automatically release the state when closed.
     */
    public @NonNull ReservedSignedState getLatestAndReserve(@NonNull final String reason) {
        try (final Locked l = lock.lock()) {
            if (map.isEmpty()) {
                return new ReservedSignedState();
            }

            final ReservedSignedState reservedSignedState = map.get(map.lastKey());
            if (reservedSignedState == null) {
                return new ReservedSignedState();
            }
            return reservedSignedState.getAndReserve(reason);
        }
    }

    /**
     * Get the latest round in this map.
     *
     * @return the latest round in this map, or {@link #NO_STATE_ROUND} if this map is empty
     */
    public long getLatestRound() {
        try (final Locked l = lock.lock()) {
            if (map.isEmpty()) {
                return NO_STATE_ROUND;
            }
            return map.lastKey();
        }
    }

    /**
     * Check if the map is empty.
     *
     * @return true if the map is empty, otherwise false.
     */
    public boolean isEmpty() {
        try (final Locked l = lock.lock()) {
            return map.isEmpty();
        }
    }

    /**
     * Add a signed state to the map.
     *
     * @param signedState the signed state to add
     * @param reason      a short description of why this SignedState is being reserved. Each location where a
     *                    SignedState is reserved should attempt to use a unique reason, as this makes debugging
     *                    reservation bugs easier.
     */
    public void put(@NonNull final SignedState signedState, @NonNull final String reason) {
        Objects.requireNonNull(signedState);
        Objects.requireNonNull(reason);

        try (final Locked l = lock.lock()) {
            final ReservedSignedState previousState =
                    map.put(signedState.getRound(), new ReservedSignedState(signedState, reason));
            if (previousState != null) {
                previousState.close();
            }
        }
    }

    /**
     * Remove a signed state from the map if it is present.
     *
     * @param round the round to remove
     */
    public void remove(final long round) {
        try (final Locked l = lock.lock()) {
            final ReservedSignedState reservedSignedState = map.remove(round);
            if (reservedSignedState != null) {
                reservedSignedState.close();
            }
        }
    }

    /**
     * Remove all signed states from this collection.
     */
    public void clear() {
        try (final Locked l = lock.lock()) {
            for (final ReservedSignedState reservedSignedState : map.values()) {
                reservedSignedState.close();
            }
            map.clear();
        }
    }

    /**
     * <p>
     * While holding a lock, execute a function that operates on an iterator of states in this map. The iterator is
     * permitted to remove elements from the map.
     * </p>
     *
     * <p>
     * Using the iterator after this method returns is strictly prohibited.
     * </p>
     *
     * @param operation an operation that will use an iterator
     */
    public void atomicIteration(@NonNull final Consumer<Iterator<SignedState>> operation) {
        try (final Locked l = lock.lock()) {
            final Iterator<ReservedSignedState> baseIterator = map.values().iterator();

            final Iterator<SignedState> iterator = new Iterator<>() {
                private ReservedSignedState previous;

                @Override
                public boolean hasNext() {
                    return baseIterator.hasNext();
                }

                @Override
                public SignedState next() {
                    previous = baseIterator.next();
                    return previous.get();
                }

                @Override
                public void remove() {
                    baseIterator.remove();
                    if (previous != null) {
                        previous.close();
                    }
                }
            };

            operation.accept(iterator);
        }
    }

    /**
     * Get the number of states in this map.
     */
    public int getSize() {
        try (final Locked l = lock.lock()) {
            return map.size();
        }
    }
}
