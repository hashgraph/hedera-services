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
package com.hedera.node.app.state.impl;

import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.WritableState;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.Keyed;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import java.util.Objects;
import javax.annotation.Nonnull;

class StateDefinitionBuilder implements StateDefinition {
    private final String stateKey;

    StateDefinitionBuilder(@Nonnull final String stateKey) {
        this.stateKey = Objects.requireNonNull(stateKey);
    }

    @Nonnull
    @Override
    public <K, V extends MerkleNode & Keyed<K>> InMemoryDefinition<K, V> inMemory() {
        return new InMemoryBuilderImpl<K, V>(stateKey);
    }

    @Nonnull
    @Override
    public <K extends VirtualKey<? super K>, V extends VirtualValue> OnDiskDefinition<K, V> onDisk(
            @Nonnull final String label) {
        return new OnDiskBuilderImpl<>(stateKey, label);
    }

    /**
     * An implementation for {@link
     * com.hedera.node.app.spi.state.StateDefinition.InMemoryDefinition}.
     *
     * @param <K> The key type
     * @param <V> The value type
     */
    private static final class InMemoryBuilderImpl<K, V extends MerkleNode & Keyed<K>>
            implements InMemoryDefinition<K, V> {
        private final String stateKey;

        InMemoryBuilderImpl(String stateKey) {
            this.stateKey = Objects.requireNonNull(stateKey);
        }

        @Nonnull
        @Override
        public WritableState<K, V> define() {
            return new InMemoryState<>(stateKey, new MerkleMap<>());
        }
    }

    /**
     * An implementation of {@link com.hedera.node.app.spi.state.StateDefinition.OnDiskDefinition}.
     *
     * @param <K> The key type
     * @param <V> The value type
     */
    private static final class OnDiskBuilderImpl<
                    K extends VirtualKey<? super K>, V extends VirtualValue>
            implements OnDiskDefinition<K, V> {
        private final String stateKey;
        private final String label;
        private final JasperDbBuilder<K, V> builder;

        OnDiskBuilderImpl(final String stateKey, final String label) {
            this.stateKey = Objects.requireNonNull(stateKey);
            this.label = Objects.requireNonNull(label);
            this.builder = new JasperDbBuilder<>();
            // TODO other serializers and such here such as the internal serializer
        }

        @Nonnull
        @Override
        public WritableState<K, V> define() {
            return new OnDiskState<>(stateKey, new VirtualMap<>(label, builder));
        }

        @Nonnull
        @Override
        public OnDiskDefinition<K, V> keySerializer(@Nonnull final KeySerializer<K> serializer) {
            builder.keySerializer(serializer);
            return this;
        }

        @Nonnull
        @Override
        public OnDiskDefinition<K, V> valueSerializer(
                @Nonnull VirtualLeafRecordSerializer<K, V> serializer) {
            builder.virtualLeafRecordSerializer(serializer);
            return this;
        }
    }
}
