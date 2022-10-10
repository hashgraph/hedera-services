package com.hedera.hashgraph.app.state;

import com.hedera.hashgraph.base.state.State;
import com.swirlds.common.merkle.MerkleNode;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A base class for implementations of {@link State}.
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public abstract class StateBase<K, V> implements State<K, V> {
    private String stateKey;
    private Set<K> readKeys = new HashSet<>();
    private Map<K, V> cachedChanges = new HashMap<>();
    private Set<K> cachedRemoves = new HashSet<>();

    /**
     * @deprecated Exists only for deserialization
     */
    @Deprecated(since = "1.0")
	StateBase() {

    }

    /**
     * Create a new StateBase.
     *
     * @param stateKey The state key. Cannot be null.
     */
    StateBase(@Nonnull String stateKey) {
        this.stateKey = Objects.requireNonNull(stateKey);
    }

    /**
     * Gets the underlying merkle node.
     *
     * @return A non-null reference to the underlying merkle node.
     */
    protected abstract @Nonnull MerkleNode getMerkle();

    ///////// NOT SURE IF THIS API IS RIGHT>>>>>>>>>>>>>>>>>>>>>>>>>>>

    /**
     * Not part of the public {@link State} interface, this method is only callable from the hedera application.
     * It commits all changes in the state to the underlying MerkleMap or VirtualMap.
     */
    public final void commit() {
        cachedRemoves.forEach(this::delete);
        cachedChanges.forEach(this::write); // probably all wrong, but whatever
    }

    protected abstract V read(K key);
    protected abstract V readForModify(K key);
    protected abstract void write(K key, V value);
    protected abstract void delete(K key);

    ////////////// END OF

    // Implementations of the API for State

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStateKey() {
        return stateKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<V> get(K key) {
        final var value = read(key);
        readKeys.add(key);
        return Optional.ofNullable(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<V> getForModify(K key) {
        final var value = readForModify(key);
        readKeys.add(key);
        return Optional.ofNullable(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(K key, V value) {
        cachedChanges.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(K key) {
        cachedRemoves.add(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<K> modifiedKeys() {
        return cachedChanges.keySet().stream();
    }
}
