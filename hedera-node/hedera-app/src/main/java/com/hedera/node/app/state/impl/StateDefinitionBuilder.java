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
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

class StateDefinitionBuilder implements StateDefinition {
    private final String stateKey;
    private final Consumer<MerkleNode> onDefine;

    StateDefinitionBuilder(
            @NonNull final String stateKey, @Nullable final Consumer<MerkleNode> onDefine) {
        this.stateKey = Objects.requireNonNull(stateKey);
        this.onDefine = onDefine;
    }

    @NonNull
    @Override
    public <K, V extends MerkleNode & Keyed<K>> InMemoryDefinition inMemory() {
        return new InMemoryBuilderImpl(stateKey);
    }

    @NonNull
    @Override
    public <K extends VirtualKey<? super K>, V extends VirtualValue> OnDiskDefinition<K, V> onDisk(
            @NonNull final String label) {
        return new OnDiskBuilderImpl<>(stateKey, label);
    }

    /**
     * An implementation for {@link
     * com.hedera.node.app.spi.state.StateDefinition.InMemoryDefinition}.
     */
    private final class InMemoryBuilderImpl implements InMemoryDefinition {
        private final String stateKey;

        InMemoryBuilderImpl(String stateKey) {
            this.stateKey = Objects.requireNonNull(stateKey);
        }

        @NonNull
        @Override
        public <K, V extends MerkleNode & Keyed<K>> WritableState<K, V> define() {
            final var merkleMap = new MerkleMap<K, V>();
            if (onDefine != null) {
                onDefine.accept(merkleMap);
            }
            return new InMemoryState<>(stateKey, merkleMap);
        }
    }

    /**
     * An implementation of {@link com.hedera.node.app.spi.state.StateDefinition.OnDiskDefinition}.
     *
     * @param <K> The key type
     * @param <V> The value type
     */
    private final class OnDiskBuilderImpl<K extends VirtualKey<? super K>, V extends VirtualValue>
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

        @NonNull
        @Override
        public WritableState<K, V> define() {
            final var virtualMap = new VirtualMap<>(label, builder);
            if (onDefine != null) {
                onDefine.accept(virtualMap);
            }
            return new OnDiskState<>(stateKey, virtualMap);
        }

        @NonNull
        @Override
        public OnDiskDefinition<K, V> keySerializer(@NonNull final KeySerializer<K> serializer) {
            builder.keySerializer(serializer);
            return this;
        }

        @NonNull
        @Override
        public OnDiskDefinition<K, V> valueSerializer(
                @NonNull VirtualLeafRecordSerializer<K, V> serializer) {
            builder.virtualLeafRecordSerializer(serializer);
            return this;
        }
    }
}
