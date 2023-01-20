package com.hedera.node.app.state.mono;

import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

class MonoWritableStates extends MonoReadableStates implements WritableStates {
    MonoWritableStates(@NonNull Map<String, Metadata> states) {
        super(states);
    }

    @NonNull
    @Override
    public <K extends Comparable<K>, V> WritableKVState<K, V> get(@NonNull String stateKey) {
        return null;
    }
}
