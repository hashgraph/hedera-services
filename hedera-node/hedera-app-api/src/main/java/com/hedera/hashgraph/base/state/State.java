package com.hedera.hashgraph.base.state;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Provides access to key/value state for a service implementation.
 *
 * @param <K> The key
 * @param <V> The value
 */
public interface State<K, V> {

    /**
     * Gets the "state key" that uniquely identifies this {@code KVState}. This must be a
     * unique value for this {@code KVState} instance. The call is idempotent, always returning
     * the same value. It must never return null.
     *
     * @return The state key. This will never be null, and will always be the same value.
     */
    String getStateKey();

    /**
     * Gets the value associated with the given key in a <strong>READ-ONLY</strong> way.
     * The returned {@link Optional} will be empty if the key does not exist in the store.
     * If the value did exist, but the expiration time has been exceeded, then the value will
     * be unset in the {@link Optional}.
     *
     * @param key The key. Cannot be null, otherwise an exception is thrown.
     * @return A non-null optional. It may be empty if there is no value for this associated key.
     * @throws IllegalArgumentException if the key is null.
     */
    Optional<V> get(K key);

    /**
     * Gets the value associated with the given key in a <strong>READ-WRITE</strong> way.
     * The returned {@link Optional} will be empty if the key does not exist in the store. If the
     * value did exist, but the expiration time has been exceeded, then the value will be unset in
     * the {@link Optional}.
     *
     * @param key The key. Cannot be null, otherwise an exception is thrown.
     * @return A non-null optional. It may be empty if there is no value for this associated key.
     * @throws IllegalArgumentException if the key is null.
     */
    Optional<V> getForModify(K key);

    /**
     * Puts the given value for the key.
     * @param key The key. Cannot be null.
     * @param value The value. Cannot be null.
     * @throws IllegalArgumentException if the key or value is null.
     */
    void put(K key, V value);

    void remove(K key);

    Stream<K> modifiedKeys();
}
