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

package com.swirlds.platform.state.signed;

import com.swirlds.common.AutoCloseableNonThrowing;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A wrapper around a signed state that holds a reservation. Until this wrapper is released/closed, the signed state
 * contained within will not be destroyed.
 */
public class ReservedSignedState implements AutoCloseableNonThrowing {

    private final SignedState signedState;
    private final String reason;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Create a wrapper around null (for scenarios where we are storing a null signed state).
     */
    ReservedSignedState() {
        this(null, "");
    }

    /**
     * Create a new reserved signed state.
     *
     * @param signedState the signed state to reserve
     * @param reason      the reason why this state was reserved
     */
    ReservedSignedState(final SignedState signedState, final String reason) {
        // TODO we should increase reference count here!
        this.signedState = signedState;
        this.reason = reason;
    }

    /**
     * Get another reservation on the signed state.
     *
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @return a new wrapper around the state that holds a new reservation
     */
    public ReservedSignedState getAndReserve(final String reason) {
        if (closed.get()) {
            throw new IllegalStateException("Can not get another reservation from a closed wrapper.");
        }
        return new ReservedSignedState(signedState, reason);
    }

    /**
     * Get the signed state. Does not take any reservations on the signed state, this is the responsibility of the
     * caller. Do not keep a reference to this signed state outside the scope of this wrapper object without properly
     * reserving it.
     *
     * @return the signed state
     */
    public SignedState get() {
        if (closed.get()) {
            throw new IllegalStateException("Can not get signed state from closed wrapper.");
        }
        return signedState;
    }

    /**
     * Release the reservation on the signed state.
     */
    @Override
    public void close() {
        final boolean prev = closed.getAndSet(true);
        if (prev) {
            throw new IllegalStateException("Already released");
        }

        if (signedState != null) {
            signedState.release(this);
        }
    }

    /**
     * Get the reason why this state was reserved.
     */
    String getReason() {
        return reason;
    }
}
