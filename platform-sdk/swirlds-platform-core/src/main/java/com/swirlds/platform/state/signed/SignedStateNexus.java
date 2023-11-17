package com.swirlds.platform.state.signed;

import com.swirlds.common.utility.Clearable;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A thread-safe container that also manages reservations for a single signed state.
 */
public class SignedStateNexus implements Consumer<ReservedSignedState>, Clearable {
    private final AtomicReference<ReservedSignedState> currentState = new AtomicReference<>();

    /**
     * Returns the current signed state and reserves it. If the current signed state is null, or cannot be reserved,
     * then null is returned.
     *
     * @param reason a short description of why this SignedState is being reserved
     * @return the current signed state, or null if there is no state set or if it cannot be reserved
     */
    public @Nullable ReservedSignedState get(@NonNull final String reason) {
        ReservedSignedState state;
        do {
            state = currentState.get();
            if (state == null) {
                return null;
            }

            // between the get method on the atomic reference and the tryGetAndReserve method on the state, the state
            // could have been closed. If this happens, tryGetAndReserve will return null
            final ReservedSignedState newReservation = state.tryGetAndReserve(reason);
            if (newReservation != null) {
                return newReservation;
            }
            // if tryGetAndReserve returned null, then we should check if set() was called in the meantime
            // if yes, then we should try again and reserve the new state
        } while (state != currentState.get());
        // this means we cannot reserve the state we are holding, this is probably an error, since we should hold a
        // reservation on it
        return null;
    }

    /**
     * Sets the current signed state to the given state, and releases the previous state if it exists.
     *
     * @param reservedSignedState the new signed state
     */
    public void set(@Nullable final ReservedSignedState reservedSignedState) {
        final ReservedSignedState oldState = currentState.getAndSet(reservedSignedState);
        if (oldState != null) {
            oldState.close();
        }
    }

    /**
     * Same as {@link #set(ReservedSignedState)} with a null argument
     */
    @Override
    public void clear() {
        set(null);
    }


    /**
     * Same as {@link #set(ReservedSignedState)}
     */
    @Override
    public void accept(@Nullable final ReservedSignedState reservedSignedState) {
        set(reservedSignedState);
    }
}
