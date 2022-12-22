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
package com.hedera.node.app.state.merkle;

import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Holds metadata related to a registered service's state.
 *
 * @param <K> The type of the state key
 * @param <V> The type of the state value
 */
public final class StateMetadata<K extends Comparable<K>, V> {
    private final String serviceName;
    private final Schema schema;
    private final StateDefinition<K, V> stateDefinition;
    private final long onDiskKeyClassId;
    private final long onDiskKeySerializerClassId;
    private final long onDiskValueClassId;
    private final long onDiskValueSerializerClassId;
    private final long inMemoryValueClassId;

    /**
     * Create an instance.
     *
     * @param serviceName The name of the service
     * @param schema The {@link Schema} that defined the state
     * @param stateDefinition The {@link StateDefinition}
     */
    public StateMetadata(
            @NonNull String serviceName,
            @NonNull Schema schema,
            @NonNull StateDefinition<K, V> stateDefinition) {
        this.serviceName = serviceName;
        this.schema = schema;
        this.stateDefinition = stateDefinition;

        this.onDiskKeyClassId = StateUtils.computeClassId(this, "OnDiskKey");
        this.onDiskKeySerializerClassId = StateUtils.computeClassId(this, "OnDiskKeySerializer");
        this.onDiskValueClassId = StateUtils.computeClassId(this, "OnDiskValue");
        this.onDiskValueSerializerClassId =
                StateUtils.computeClassId(this, "OnDiskValueSerializer");
        this.inMemoryValueClassId = StateUtils.computeClassId(this, "InMemoryValue");
    }

    public String serviceName() {
        return serviceName;
    }

    public Schema schema() {
        return schema;
    }

    public StateDefinition<K, V> stateDefinition() {
        return stateDefinition;
    }

    public long onDiskKeyClassId() {
        return onDiskKeyClassId;
    }

    public long onDiskKeySerializerClassId() {
        return onDiskKeySerializerClassId;
    }

    public long onDiskValueClassId() {
        return onDiskValueClassId;
    }

    public long onDiskValueSerializerClassId() {
        return onDiskValueSerializerClassId;
    }

    public long inMemoryValueClassId() {
        return inMemoryValueClassId;
    }
}
