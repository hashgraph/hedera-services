/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.datasource;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.io.SelfSerializable;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Objects;

/**
 * An object for leaf data. The leaf record contains the path, key, and value.
 * This record is {@link SelfSerializable} to support reconnect and state saving, where it is necessary
 * to take leaf records from caches that are not yet flushed to disk and write them to the stream.
 * We never send hashes in the stream.
 */
public final class VirtualLeafRecord<V> {

    private final long path;

    private final Bytes keyBytes;

    private V value;
    private Codec<V> valueCodec;
    private Bytes valueBytes;

    private VirtualLeafRecord(
            final long path,
            @NonNull final Bytes keyBytes,
            @Nullable final V value,
            @Nullable final Codec<V> valueCodec,
            @Nullable final Bytes valueBytes) {
        this.path = path;
        this.keyBytes = Objects.requireNonNull(keyBytes);
        this.value = value;
        this.valueCodec = valueCodec;
        this.valueBytes = valueBytes;
        if ((value != null) && (valueBytes != null)) {
            throw new IllegalStateException("Either value or valueBytes may be not null, but not both");
        }
    }

    public VirtualLeafRecord(
            final long path,
            @NonNull final Bytes keyBytes,
            @Nullable final V value,
            @Nullable final Codec<V> valueCodec) {
        this(path, keyBytes, value, valueCodec, null);
    }

    public VirtualLeafRecord(@NonNull final VirtualLeafBytes<V> leafBytes) {
        this(leafBytes.path(), leafBytes.keyBytes(), null, null, leafBytes.valueBytes());
    }

    public long getPath() {
        return path;
    }

    @NonNull
    public Bytes getKeyBytes() {
        return keyBytes;
    }

    @Nullable
    public V getValue(final Codec<V> valueCodec) {
        if (value == null) {
            // No synchronization here. In the worst case, value will be initialized multiple
            // times, but always to the same object
            if (valueBytes != null) {
                assert this.valueCodec == null;
                this.valueCodec = valueCodec;
                try {
                    value = valueCodec.parse(valueBytes);
                } catch (final ParseException e) {
                    throw new RuntimeException("Failed to deserialize a value from bytes", e);
                }
            } else {
                // valueBytes is null, so the value should be null, too. Does it make sense to
                // do anything to the codec here? Perhaps not
            }
        } else {
            // The value is provided or already parsed from bytes. Check the codec
            assert valueCodec != null;
            if (!this.valueCodec.equals(valueCodec)) {
                throw new IllegalStateException("Value codec mismatch");
            }
        }
        return value;
    }

    @Nullable
    public Bytes getValueBytes() {
        if (valueBytes == null) {
            assert (value == null) || (valueCodec != null);
            // No synchronization here. In the worst case, valueBytes will be initialized multiple
            // times, but always to the same value
            if (value != null) {
                final byte[] vb = new byte[valueCodec.measureRecord(value)];
                try {
                    valueCodec.write(value, BufferedData.wrap(vb));
                    valueBytes = Bytes.wrap(vb);
                } catch (final IOException e) {
                    throw new RuntimeException("Failed to serialize a value to bytes", e);
                }
            }
        }
        return valueBytes;
    }

    public VirtualLeafRecord<V> withPath(final long newPath) {
        return new VirtualLeafRecord<>(newPath, keyBytes, value, valueCodec, valueBytes);
    }

    public VirtualLeafRecord<V> withValue(final V newValue) {
        return new VirtualLeafRecord<>(path, keyBytes, newValue, valueCodec, null);
    }
}
