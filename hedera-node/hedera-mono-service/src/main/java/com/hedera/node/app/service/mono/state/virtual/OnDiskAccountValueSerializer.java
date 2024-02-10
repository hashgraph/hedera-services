/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual;

import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskAccount;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.merkledb.serialize.ValueSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.Objects;

public class OnDiskAccountValueSerializer implements ValueSerializer<OnDiskAccount> {

    // Serializer class ID
    static final long CLASS_ID = 0xe5d01987257f5efdL;

    // Serializer version
    static final int CURRENT_VERSION = 1;

    // Value data version
    static final int DATA_VERSION = 1;

    // Serializer info

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    // Data version

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    // Value serialization

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getSerializedSize(OnDiskAccount value) {
        return value.getSerializedSizeInBytes();
    }

    @Override
    public int getTypicalSerializedSize() {
        return OnDiskAccount.getTypicalSerializedSize();
    }

    @Override
    public void serialize(@NonNull final OnDiskAccount value, @NonNull final WritableSequentialData out) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(out);
        value.serialize(out);
    }

    @Override
    @Deprecated
    public void serialize(final OnDiskAccount value, final ByteBuffer buffer) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(buffer);
        value.serialize(buffer);
    }

    // Value deserializatioin

    @Override
    public OnDiskAccount deserialize(@NonNull final ReadableSequentialData in) {
        Objects.requireNonNull(in);
        final OnDiskAccount value = new OnDiskAccount();
        value.deserialize(in);
        return value;
    }

    @Override
    @Deprecated
    public OnDiskAccount deserialize(final ByteBuffer buffer, final long version) {
        Objects.requireNonNull(buffer);
        final OnDiskAccount value = new OnDiskAccount();
        value.deserialize(buffer, (int) version);
        return value;
    }
}
