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

import static com.swirlds.base.ArgumentUtils.throwArgNull;
import static com.swirlds.platform.state.signed.SignedStateUtilities.newSignedStateWrapper;

import com.swirlds.common.utility.AutoCloseableWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * A thread safe map-like object for storing a number of states. This object automatically manages reservations.
 */
public class SignedStateMap {

    private final SortedMap<Long, SignedState> map = new TreeMap<>();

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
     * @param round the round to get
     * @return an auto-closable object that wraps a signed state. May point to a null state if there is no state for the
     * given round. Will automatically release the state when closed.
     */
    public synchronized @NonNull AutoCloseableWrapper<SignedState> get(final long round) {
        return newSignedStateWrapper(map.get(round));
    }

    /**
     * Get the latest state in this map. A reservation is taken on the state before this method returns.
     *
     * @return an auto-closable object that wraps a signed state. May point to a null state if there are no states in
     * this container. Will automatically release the state when closed.
     */
    public synchronized @NonNull AutoCloseableWrapper<SignedState> getLatest() {
        if (map.isEmpty()) {
            return newSignedStateWrapper(null);
        }
        return newSignedStateWrapper(map.get(map.lastKey()));
    }

    /**
     * Get the latest round in this map.
     *
     * @return the latest round in this map, or {@link #NO_STATE_ROUND} if this map is empty
     */
    public synchronized long getLatestRound() {
        if (map.isEmpty()) {
            return NO_STATE_ROUND;
        }
        return map.lastKey();
    }

    /**
     * Check if the map is empty.
     *
     * @return true if the map is empty, otherwise false.
     */
    public synchronized boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Add a signed state to the map.
     *
     * @param signedState the signed state to add
     */
    public synchronized void put(@NonNull final SignedState signedState) {
        throwArgNull(signedState, "signedState");

        signedState.reserve();

        final SignedState previousState = map.put(signedState.getRound(), signedState);

        if (previousState != null) {
            signedState.release();
        }
    }

    /**
     * Remove a signed state from the map if it is present.
     *
     * @param round the round to remove
     */
    public synchronized void remove(final long round) {
        final SignedState signedState = map.remove(round);
        if (signedState != null) {
            signedState.release();
        }
    }

    /**
     * Remove all signed states from this collection.
     */
    public synchronized void clear() {
        for (final SignedState signedState : map.values()) {
            signedState.release();
        }
        map.clear();
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
    public synchronized void atomicIteration(@NonNull final Consumer<Iterator<SignedState>> operation) {

        final Iterator<SignedState> baseIterator = map.values().iterator();

        final Iterator<SignedState> iterator = new Iterator<>() {
            private SignedState previous;

            @Override
            public boolean hasNext() {
                return baseIterator.hasNext();
            }

            @Override
            public SignedState next() {
                previous = baseIterator.next();
                return previous;
            }

            @Override
            public void remove() {
                baseIterator.remove();
                if (previous != null) {
                    previous.release();
                }
            }
        };

        operation.accept(iterator);
    }

    /**
     * Get the number of states in this map.
     */
    public synchronized int getSize() {
        return map.size();
    }
}
