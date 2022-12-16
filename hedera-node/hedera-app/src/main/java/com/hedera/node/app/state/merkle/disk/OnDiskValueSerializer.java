/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle.disk;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.StateUtils;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/**
 * An implementation of {@link SelfSerializableSupplier}, required by the {@link
 * com.swirlds.virtualmap.VirtualMap} for creating new {@link OnDiskValue}s.
 *
 * @param <V> The type of the value in the virtual map
 */
public final class OnDiskValueSerializer<V> implements SelfSerializableSupplier<OnDiskValue<V>> {
    /** The metadata */
    private final StateMetadata<?, V> md;

    /**
     * Create a new instance. This is created at registration time, it doesn't need to serialize
     * anything to disk.
     *
     * @param md The metadata to use
     */
    public OnDiskValueSerializer(@NonNull final StateMetadata<?, V> md) {
        this.md = Objects.requireNonNull(md);
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return StateUtils.computeClassId(md.serviceName(), md.stateKey(), "on-disk-value");
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return 1;
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        // This class has nothing to serialize
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int ignored)
            throws IOException {
        // This class has nothing to deserialize
    }

    /** {@inheritDoc} */
    @Override
    public OnDiskValue<V> get() {
        return new OnDiskValue<>(md.valueParser(), md.valueWriter());
    }
}
