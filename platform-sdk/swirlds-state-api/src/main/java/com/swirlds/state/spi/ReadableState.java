package com.swirlds.state.spi;

import com.swirlds.state.lifecycle.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface ReadableState {

    /**
     * Gets the "state key" that uniquely identifies this {@link ReadableState} within the
     * {@link Schema} which are scoped to the service implementation. The key is therefore not globally
     * unique, only unique within the service implementation itself.
     *
     * <p>The call is idempotent, always returning the same value. It must never return null.
     *
     * @return The state key. This will never be null, and will always be the same value for an
     *     instance of {@link ReadableState}.
     */
    @NonNull
    String getStateKey(); // TODO: remove?

    /**
     * Gets the "state id" that uniquely identifies this {@link ReadableState} within the
     * {@link com.swirlds.state.lifecycle.Service} and {@link com.hedera.pbj.runtime.Schema}. It is globally unique.
     *
     * <p>The call is idempotent, always returning the same value.
     *
     * @return The state id. This will always be the same value for an
     *     instance of {@link ReadableState}.
     */
    int getStateId();

    @NonNull
    String getLabel();
}
