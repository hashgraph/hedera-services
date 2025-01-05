package com.hedera.node.app.hints.impl;

import com.hedera.node.app.hints.ReadableHintsStore;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

import static java.util.Objects.requireNonNull;

/**
 * Placeholder implementation of the {@link ReadableHintsStore}.
 */
public class ReadableHintsStoreImpl implements ReadableHintsStore {
    public ReadableHintsStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
    }
}
