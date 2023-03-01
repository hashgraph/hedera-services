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

/**
 * Similar to an {@link java.util.concurrent.atomic.AtomicReference AtomicReference&lt;SignedState&gt;}, but with the
 * additional functionality of holding a reservation on the contained state.
 */
public class SignedStateReference {

    private ReservedSignedState reservedSignedState = new ReservedSignedState();

    /**
     * Create a new signed state reference with a null initial state.
     */
    public SignedStateReference() {}

    /**
     * Create a new signed state reference with an initial state.
     *
     * @param signedState a signed state
     * @param reason      a short description of why this SignedState is being reserved. Each location where a
     *                    SignedState is reserved should attempt to use a unique reason, as this makes debugging
     *                    reservation bugs easier.
     */
    public SignedStateReference(final SignedState signedState, final String reason) {
        set(signedState, reason);
    }

    /**
     * Set a signed state, replacing the existing state.
     *
     * @param signedState a signed state, may be null
     * @param reason      a short description of why this SignedState is being reserved. Each location where a
     *                    SignedState is reserved should attempt to use a unique reason, as this makes debugging
     *                    reservation bugs easier.
     */
    public synchronized void set(final SignedState signedState, final String reason) {
        if (signedState == reservedSignedState.get()) {
            // Same object, no action required
            return;
        }

        reservedSignedState.close();
        reservedSignedState =
                signedState == null ? new ReservedSignedState() : new ReservedSignedState(signedState, reason);
    }

    /**
     * Check if the current value referenced by this object is null.
     *
     * @return true if the referenced state is null
     */
    public synchronized boolean isNull() {
        return reservedSignedState == null;
    }

    /**
     * Get the round of the signed state contained by this reference.
     *
     * @return the signed state's round, or -1 if this object references a null object
     */
    public synchronized long getRound() {
        final SignedState signedState = reservedSignedState.get();
        return signedState == null ? -1 : signedState.getRound();
    }

    /**
     * Get the signed state and take a reservation on it.
     *
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @return an autocloseable wrapper with the state, when closed the state is released
     */
    public ReservedSignedState getAndReserve(final String reason) {
        return reservedSignedState.getAndReserve(reason);
    }
}
