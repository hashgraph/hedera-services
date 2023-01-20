package com.hedera.node.app.state.mono;

import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

class MonoReadableStates implements ReadableStates {
    public enum MerkleKind { MERKLE_MAP, VIRTUAL_MAP, MERKLE_NODE, N_ARY }
    public record Metadata (MerkleKind kind, int childIndex, Map<String, Metadata> children) {
        public Metadata(MerkleKind kind, int childIndex) {
            this(kind, childIndex, Collections.emptyMap());
        }
        public Metadata(int childIndex, Map<String, Metadata> children) {
            this(MerkleKind.N_ARY, childIndex, children);
        }
    }

    private final Map<String, Metadata> states;

    MonoReadableStates(@NonNull final Map<String, Metadata> states) {
        this.states = Objects.requireNonNull(states);
    }

    @NonNull
    @Override
    public <K extends Comparable<K>, V> ReadableKVState<K, V> get(@NonNull String stateKey) {
        return null;
    }

    @Override
    public boolean contains(@NonNull String stateKey) {
        return states.containsKey(stateKey);
    }

    @NonNull
    @Override
    public Set<String> stateKeys() {
        return states.keySet();
    }
}
