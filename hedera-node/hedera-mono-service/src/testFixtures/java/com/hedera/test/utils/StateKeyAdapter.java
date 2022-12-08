package com.hedera.test.utils;

import com.hedera.node.app.spi.state.State;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

public class StateKeyAdapter<K1, K2, V> implements State<K2, V> {
    private final State<K1, V> delegate;
    private final Function<K2, K1> keyAdapter;

    public StateKeyAdapter(final State<K1, V> delegate, final Function<K2, K1> keyAdapter) {
        this.delegate = delegate;
        this.keyAdapter = keyAdapter;
    }

    @Override
    public String getStateKey() {
        return delegate.getStateKey();
    }

    @Override
    public Optional<V> get(final K2 key) {
        return delegate.get(keyAdapter.apply(key));
    }

    @Override
    public Instant getLastModifiedTime() {
        return delegate.getLastModifiedTime();
    }
}
