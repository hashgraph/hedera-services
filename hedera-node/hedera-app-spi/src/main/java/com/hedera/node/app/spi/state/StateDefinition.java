/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import javax.annotation.Nonnull;

/**
 * Creates new {@link WritableState} definition using a fluent-API.
 *
 * <p>During construction of a service instance, the service needs the ability to define its state.
 * Is it to be stored in memory, or on disk? This is a critical consideration for performance and
 * scalability reasons. What are the keys and values to be used with that state? This class is used
 * to create those definitions.
 */
public interface StateDefinition {

    /**
     * Create a new {@link InMemoryDefinition} for a {@link WritableState}.
     *
     * @return a non-null {@link InMemoryDefinition}.
     * @param <K> The key type to use for the in memory state
     * @param <V> The value type to use for the in memory state
     */
    @Nonnull
    <K, V extends MerkleNode & Keyed<K>> InMemoryDefinition<K, V> inMemory();

    /**
     * Create a new {@link OnDiskDefinition} for a {@link WritableState}.
     *
     * @param label The label to use with the virtual map. Cannot be null.
     * @return a non-null {@link InMemoryDefinition}.
     * @param <K> The key type to use for the on disk state
     * @param <V> The value type to use for the on disk state
     */
    @Nonnull
    <K extends VirtualKey<? super K>, V extends VirtualValue> OnDiskDefinition<K, V> onDisk(@Nonnull String label);

    /**
     * A fluent-API for defining configuration for in-memory state.
     *
     * @param <K> The key type to use for the in memory state
     * @param <V> The value type to use for the in memory state
     */
    interface InMemoryDefinition<K, V extends MerkleNode & Keyed<K>> {
        /**
         * Finishes the definition of this state.
         *
         * @return A new {@link WritableState} based on the definition. This state should
         *     <b>NEVER</b> be held onto, it exists so the {@link StateRegistryCallback} can use it
         *     for migration purposes.
         */
        @Nonnull
        WritableState<K, V> define();
    }

    /**
     * A fluent-API for defining configuration for on-disk state.
     *
     * @param <K> The key type to use for the in memory state
     * @param <V> The value type to use for the in memory state
     */
    interface OnDiskDefinition<K extends VirtualKey<? super K>, V extends VirtualValue> {
        /**
         * The serializer to use with the key type, for storing on disk.
         *
         * @param serializer The serializer to use.
         * @return a reference to this definer.
         */
        @Nonnull
        OnDiskDefinition<K, V> keySerializer(@Nonnull KeySerializer<K> serializer);

        /**
         * The serializer to use with the value type, for storing on disk.
         *
         * @param serializer The serializer to use.
         * @return a reference to this definer.
         */
        @Nonnull
        OnDiskDefinition<K, V> valueSerializer(
                @Nonnull VirtualLeafRecordSerializer<K, V> serializer);

        /**
         * Finishes the definition of this state.
         *
         * @return A new {@link WritableState} based on the definition. This state should
         *     <b>NEVER</b> be held onto, it exists so the {@link StateRegistryCallback} can use it
         *     for migration purposes.
         */
        @Nonnull
        WritableState<K, V> define();
    }
}
