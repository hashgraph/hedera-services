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

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.exceptions.ReferenceCountException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A wrapper around a signed state that holds a reservation. Until this wrapper is released/closed, the signed state
 * contained within will not be destroyed.
 * <p>
 * This class is not thread safe. That is, it is not safe for one thread to access this object while another thread is
 * asynchronously closing it. Each thread should hold its own reservation on a state if it needs to access a state.
 */
public final class ReservedSignedState implements AutoCloseableNonThrowing { // TODO test this class

    private static final Logger logger = LogManager.getLogger(ReservedSignedState.class);

    private final SignedState signedState;
    private final String reason;
    private final long reservationId = nextReservationId.getAndIncrement();
    private boolean closed = false;

    /**
     * The next reservation id to use. It is ok that this is static, since we don't care which ID any particular
     * reservation has, as long as that ID is unique.
     */
    private static final AtomicLong nextReservationId = new AtomicLong(0);

    /**
     * Create a wrapper around null (for scenarios where we are storing a null signed state).
     */
    public ReservedSignedState() {
        this.signedState = null;
        this.reason = "";
    }

    /**
     * Create a new reserved signed state.
     *
     * @param signedState the signed state to reserve
     * @param reason      a short description of why this SignedState is being reserved. Each location where a
     *                    SignedState is reserved should attempt to use a unique reason, as this makes debugging
     *                    reservation bugs easier.
     */
    ReservedSignedState(@NonNull final SignedState signedState, @NonNull final String reason) {
        this.signedState = Objects.requireNonNull(signedState);
        this.reason = Objects.requireNonNull(reason);

        // It is safe to "leak this" here.
        // All fields are final, this class is final, and everything has been instantiated.
        signedState.incrementReservationCount(this);
    }

    /**
     * Check if the signed state is null.
     *
     * @return true if the signed state is null, false otherwise
     */
    public boolean isNull() {
        throwIfClosed();
        return signedState == null;
    }

    /**
     * Check if the signed state is not null. If this method returns true then it is not strictly required to call
     * {@link #close} on this object.
     *
     * @return true if the signed state is not null, false otherwise
     */
    public boolean isNotNull() {
        throwIfClosed();
        return signedState != null;
    }

    /**
     * Get another reservation on the signed state.
     *
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @return a new wrapper around the state that holds a new reservation
     */
    public @NonNull ReservedSignedState getAndReserve(@NonNull final String reason) {
        throwIfClosed();
        return new ReservedSignedState(signedState, reason);
    }

    /**
     * Get the signed state. Does not take any reservations on the signed state, this is the responsibility of the
     * caller. Do not keep a reference to this signed state outside the scope of this wrapper object without properly
     * reserving it.
     *
     * @return the signed state
     * @throws NullPointerException if the signed state is null
     */
    public @NonNull SignedState get() {
        throwIfClosed();
        return Objects.requireNonNull(signedState);
    }

    /**
     * Release the reservation on the signed state.
     */
    @Override
    public void close() {
        throwIfClosed();
        closed = true;
        if (signedState != null) {
            signedState.decrementReservationCount(this);
        }
    }

    /**
     * Get the reason why this state was reserved.
     */
    @NonNull
    String getReason() {
        return reason;
    }

    /**
     * Get the reservation id.
     */
    long getReservationId() {
        return reservationId;
    }

    /**
     * Throw an exception if this wrapper has been closed.
     */
    private void throwIfClosed() {
        if (closed) {
            if (signedState != null) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "This ReservedSignedState has already been closed, dumping history\n{},",
                        signedState.getHistory());
            }
            throw new ReferenceCountException("This ReservedSignedState has been closed.");
        }
    }
}
