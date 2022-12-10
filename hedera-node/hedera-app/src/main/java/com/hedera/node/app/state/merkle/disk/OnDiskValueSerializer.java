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
package com.hedera.node.app.state.merkle.disk;

import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.StateUtils;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

public final class OnDiskValueSerializer<V> implements SelfSerializableSupplier<OnDiskValue<V>> {
    private final StateMetadata<?, V> md;

    public OnDiskValueSerializer(@NonNull final StateMetadata<?, V> md) {
        this.md = Objects.requireNonNull(md);
    }

    @Override
    public long getClassId() {
        return StateUtils.computeValueClassId(md.serviceName(), md.stateKey());
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        // This class has nothing to serialize
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int ignored)
            throws IOException {
        // This class has nothing to deserialize
    }

    @Override
    public OnDiskValue<V> get() {
        return new OnDiskValue<>(md.valueParser(), md.valueWriter());
    }
}
