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

import com.swirlds.common.utility.AutoCloseableWrapper;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A thread safe map-like object for storing a number of states. This object automatically manages reservations.
 */
public class SignedStateMap {

    private final Map<Long, ReservedSignedState> map = new HashMap<>();

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
    public synchronized ReservedSignedState get(final long round, final String reason) {
        final ReservedSignedState reservedSignedState = map.get(round);
        if (reservedSignedState == null) {
            // TODO is this the correct behavior?
            return new ReservedSignedState();
        }
        return reservedSignedState.getAndReserve(reason);
    }

    /**
     * Add a signed state to the map.
     *
     * @param signedState the signed state to add
     * @param reason      a short description of why this SignedState is being reserved. Each location where a
     *                    SignedState is reserved should attempt to use a unique reason, as this makes debugging
     *                    reservation bugs easier.
     */
    public synchronized void put(final SignedState signedState, final String reason) {
        Objects.requireNonNull(signedState, "signedState");

        final ReservedSignedState previousState =
                map.put(signedState.getRound(), new ReservedSignedState(signedState, reason));
        if (previousState != null) {
            previousState.close();
        }
    }

    /**
     * Remove a signed state from the map if it is present.
     *
     * @param round the round to remove
     */
    public synchronized void remove(final long round) {
        final ReservedSignedState reservedSignedState = map.remove(round);
        if (reservedSignedState != null) {
            reservedSignedState.close();
        }
    }

    /**
     * Remove all signed states from this collection.
     */
    public synchronized void clear() {
        for (final ReservedSignedState reservedSignedState : map.values()) {
            reservedSignedState.close();
        }
        map.clear();
    }

    /**
     * Finds the first signed state matching the supplied {@code predicate} and returns it with a reservation.
     *
     * @param predicate the search criteria
     * @param reason    a short description of why this SignedState is being reserved. Each location where a SignedState
     *                  is reserved should attempt to use a unique reason, as this makes debugging reservation bugs
     *                  easier.
     * @return an {@link AutoCloseableWrapper} with the first matching signed state with the specified reservation take
     * out on it, or an {@link AutoCloseableWrapper} with null if none was found
     */
    public synchronized ReservedSignedState find(final Predicate<SignedState> predicate, final String reason) {
        for (final ReservedSignedState reservedSignedState : map.values()) {
            if (predicate.test(reservedSignedState.get())) {
                return reservedSignedState.getAndReserve(reason);
            }
        }
        return new ReservedSignedState();
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
    public synchronized void atomicIteration(final Consumer<Iterator<SignedState>> operation) {

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

    /**
     * Get the number of states in this map.
     */
    public synchronized int getSize() {
        return map.size();
    }
}
