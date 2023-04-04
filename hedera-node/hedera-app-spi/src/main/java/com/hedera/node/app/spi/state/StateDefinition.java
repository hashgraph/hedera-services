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

package com.hedera.node.app.spi.state;

import com.hedera.pbj.runtime.Codec;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * @param stateKey The "state key" that uniquely identifies this {@link ReadableKVState} within the
 *     {@link Schema} which are scoped to the service implementation. The key is therefore not
 *     globally unique, only unique within the service implementation itself.
 * @param keyCodec The {@link Codec} to use for parsing and writing keys in the registered state
 * @param valueCodec The {@link Codec} to use for parsing and writing values in the registered
 *     state
 * @param maxKeysHint A hint as to the maximum number of keys to be stored in this state. This value
 *     CANNOT CHANGE from one schema version to another. If it is changed, you will need to do a
 *     long-form migration to a new state.
 * @param onDisk Whether to store this state on disk
 * @param <K> The type of key
 * @param <V> The type of value
 */
public record StateDefinition<K extends Comparable<? super K>, V>(
        @NonNull String stateKey,
        @Nullable Codec<K> keyCodec,
        @NonNull Codec<V> valueCodec,
        int maxKeysHint,
        boolean onDisk,
        boolean singleton) {

    private static final int NO_MAX = -1;

    public StateDefinition {
        if (singleton && onDisk) {
            throw new IllegalArgumentException("A state cannot both be 'singleton' and 'onDisk'");
        }

        if (onDisk && maxKeysHint <= 0) {
            throw new IllegalArgumentException("You must specify the maxKeysHint when onDisk. Please see docs.");
        }

        if (!singleton && keyCodec == null) {
            throw new NullPointerException("keyCodec must be specified when not using singleton types");
        }
    }

    /**
     * Convenience method for creating a {@link StateDefinition} for in-memory k/v states.
     *
     * @param stateKey The state key
     * @param keyCodec The codec for the key
     * @param valueCodec The codec for the value
     * @return An instance of {@link StateDefinition}
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K extends Comparable<? super K>, V> StateDefinition<K, V> inMemory(
            @NonNull final String stateKey, @NonNull final Codec<K> keyCodec, @NonNull final Codec<V> valueCodec) {
        return new StateDefinition<>(stateKey, keyCodec, valueCodec, NO_MAX, false, false);
    }

    /**
     * Convenience method for creating a {@link StateDefinition} for on-disk k/v states.
     *
     * @param stateKey The state key
     * @param keyCodec The codec for the key
     * @param valueCodec The codec for the value
     * @param maxKeysHint A hint as to the maximum number of keys to be stored in this state. This
     *     value * CANNOT CHANGE from one schema version to another. If it is changed, you will need
     *     to do a * long-form migration to a new state.
     * @return An instance of {@link StateDefinition}
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K extends Comparable<? super K>, V> StateDefinition<K, V> onDisk(
            @NonNull final String stateKey,
            @NonNull final Codec<K> keyCodec,
            @NonNull final Codec<V> valueCodec,
            final int maxKeysHint) {
        return new StateDefinition<>(stateKey, keyCodec, valueCodec, maxKeysHint, true, false);
    }

    /**
     * Convenience method for creating a {@link StateDefinition} for singleton states.
     *
     * @param stateKey The state key
     * @param valueCodec The codec for the singleton value
     * @return An instance of {@link StateDefinition}
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K extends Comparable<K>, V> StateDefinition<K, V> singleton(
            @NonNull final String stateKey, @NonNull final Codec<V> valueCodec) {
        return new StateDefinition<>(stateKey, null, valueCodec, NO_MAX, false, true);
    }
}
