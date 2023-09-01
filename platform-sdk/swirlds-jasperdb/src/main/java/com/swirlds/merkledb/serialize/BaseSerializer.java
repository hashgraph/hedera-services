/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.serialize;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public interface BaseSerializer<T> {

    /** Data size constant used when the data size is variable */
    int VARIABLE_DATA_SIZE = -1;

    /** Get the current data item serialization version */
    long getCurrentDataVersion();

    /**
     * Get if the number of bytes a data item takes when serialized is variable or fixed
     *
     * @return true if getSerializedSize() == DataFileCommon.VARIABLE_DATA_SIZE
     */
    // Future work: remove this method
    default boolean isVariableSize() {
        return getSerializedSize() == VARIABLE_DATA_SIZE;
    }

    /**
     * Get the number of bytes a data item takes when serialized
     *
     * @return Either a number of bytes or DataFileCommon.VARIABLE_DATA_SIZE if size is variable
     */
    // Future work: remove this method
    int getSerializedSize();

    @Deprecated(forRemoval = true)
    default int getSerializedSizeForVersion(long version) {
        return getSerializedSize();
    }

    /**
     * For variable sized data items get the typical number of bytes an item takes when serialized.
     *
     * @return Either for fixed size same as getSerializedSize() or an estimated typical size
     */
    default int getTypicalSerializedSize() {
        if (isVariableSize()) {
            throw new IllegalStateException("Variable sized implementations have to override this method");
        }
        return getSerializedSize();
    }

    default int getSerializedSize(@NonNull final T data) {
        Objects.requireNonNull(data);
        final int size = getSerializedSize();
        if (size != VARIABLE_DATA_SIZE) {
            return size;
        }
        throw new RuntimeException("TO IMPLEMENT: " + getClass().getSimpleName() + ".getSerializedSize()");
    }

    /**
     * Serialize a data item including header to the byte buffer returning the size of the data
     * written. Serialization format must be identical to {@link #deserialize(ByteBuffer, long)}.
     *
     * @param data The data item to serialize
     * @param buffer Output buffer to write to
     * @return Number of bytes written
     */
    @Deprecated(forRemoval = true)
    int serialize(T data, ByteBuffer buffer) throws IOException;

    void serialize(@NonNull final T dataItem, @NonNull final WritableSequentialData out);

    /**
     * Deserialize a data item from a byte buffer, that was written with given data version.
     *
     * @param buffer The buffer to read from containing the data item including its header
     * @param dataVersion The serialization version the data item was written with
     * @return Deserialized data item
     */
    @Deprecated(forRemoval = true)
    T deserialize(ByteBuffer buffer, long dataVersion) throws IOException;

    T deserialize(@NonNull final ReadableSequentialData in);
}
