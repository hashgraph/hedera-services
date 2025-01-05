package com.hedera.node.app.hints.impl;

import com.hedera.node.app.hints.WritableHintsStore;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Placeholder implementation of the {@link WritableHintsStore}.
 */
public class WritableHintsStoreImpl extends  ReadableHintsStoreImpl implements WritableHintsStore {
    public WritableHintsStoreImpl(@NonNull WritableStates states) {
        super(states);
    }
}
