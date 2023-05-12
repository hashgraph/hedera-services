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

package com.swirlds.jasperdb.files.hashmap;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.jasperdb.files.BaseSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Interface for serializers of hash map keys. This is very similar to a DataItemSerializer but only serializes a key.
 * The key can serialize to fixed number or variable number of bytes.
 * <p>
 * The reason that the VirtualKey's own serialization is not used here is because it is very important that the data
 * written for each key is as small as possible. For that reason serialization is version once per file not once per
 * key written.
 * </p>
 *
 * @param <K>
 * 		the class for a key
 */
public interface KeySerializer<K> extends BaseSerializer<K>, SelfSerializable {

    /**
     * Get the current key serialization version. Key serializers can only use the lower 32 bits of the version
     * long as the upper 32 are used by the BucketSerializer.
     */
    long getCurrentDataVersion();

    /**
     * @return the index to use for indexing the keys that this KeySerializer creates
     */
    default KeyIndexType getIndexType() {
        return getSerializedSize() == Long.BYTES ? KeyIndexType.SEQUENTIAL_INCREMENTING_LONGS : KeyIndexType.GENERIC;
    }

    /**
     * Deserialize key size from the given byte buffer
     *
     * @param buffer
     * 		Buffer to read from
     * @return The number of bytes used to store the key, including for storing the key size if needed.
     */
    int deserializeKeySize(ByteBuffer buffer);

    /**
     * Compare keyToCompare's data to that contained in the given ByteBuffer. The data in the buffer is assumed to be
     * starting at the current buffer position and in the format written by this class's serialize() method. The reason
     * for this rather than just deserializing then doing an object equals is performance. By doing the comparison here
     * you can fail fast on the first byte that does not match. As this is used in a tight loop in searching a hash map
     * bucket for a match performance is critical.
     *
     * @param buffer
     * 		The buffer to read from and compare to
     * @param dataVersion
     * 		The serialization version of the data in the buffer
     * @param keyToCompare
     * 		The key to compare with the data in the file.
     * @return true if the content of the buffer matches this class's data
     * @throws IOException
     * 		If there was a problem reading from the buffer
     */
    boolean equals(ByteBuffer buffer, int dataVersion, K keyToCompare) throws IOException;
}
