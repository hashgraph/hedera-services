/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import java.util.Objects;

/**
 * Each data item needs a header containing at least a numeric key. The key can be any size from byte to long. The size
 * can be stored for variable size data or this can be constructed with a fixed size.
 */
public class DataItemHeader {

    /** the size of bytes for the data item, this includes the data item header. */
    private final int sizeBytes;

    /** the key for data item, the key may be smaller than long up to size of long */
    private final long key;

    public DataItemHeader(final int sizeBytes, final long key) {
        this.sizeBytes = sizeBytes;
        this.key = key;
    }

    /**
     * Get the size of bytes for the data item, this includes the data item header.
     */
    public int getSizeBytes() {
        return sizeBytes;
    }

    /**
     * Get the key for data item, the key may be smaller than long up to size of long
     */
    public long getKey() {
        return key;
    }

    @Override
    public String toString() {
        return "DataItemHeader{" + "size=" + sizeBytes + ", key=" + key + '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataItemHeader that)) {
            return false;
        }
        return sizeBytes == that.sizeBytes && key == that.key;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sizeBytes, key);
    }
}
