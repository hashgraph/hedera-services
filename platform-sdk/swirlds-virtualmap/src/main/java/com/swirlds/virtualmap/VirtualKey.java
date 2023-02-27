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

package com.swirlds.virtualmap;

import com.swirlds.common.io.SelfSerializable;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A virtual key, specifically for use with the Virtual FCMap {@code VirtualMap}. The indexes
 * used for looking up values are all stored on disk in order to support virtualization to
 * massive numbers of entities. This requires that any key used with the {@code VirtualMap}
 * needs to be serializable. To improve performance, this interface exposes some methods that
 * avoid instance creation and serialization for normal key activities like equality.
 * <p>
 * Keys must implement {@link Comparable}.
 */
public interface VirtualKey<T extends Comparable<? super T>> extends SelfSerializable, Comparable<T> {

    /**
     * This needs to be a very good quality hash code with even spread, or it will be very inefficient when used in
     * HalfDiskHashMap.
     *
     * @return Strong well distributed hash code
     */
    @Override
    int hashCode();

    /**
     * Serialize to a ByteBuffer. This serialization's data should match that of the stream serialization of
     * SelfSerializable so the data can be written by one and read by the other. The reason for having the extra method
     * here is that it is inefficient in a hot spot area to have to wrap a ByteBuffer into an input or output stream for
     * every small read or write.
     *
     * Just like SelfSerializable we do not write our classes version here as that is handled by the calling class.
     *
     * @param buffer
     * 		The buffer to serialize into, at the current position of that buffer. The buffers position should
     * 		not be changed other than it being incremented by the amount of data written.
     * @throws IOException
     * 		If there was a problem writing this classes data into the ByteBuffer
     */
    void serialize(ByteBuffer buffer) throws IOException;

    /**
     * Deserialize from a ByteBuffer. This should read the data that was written by serialize(ByteBuffer buffer) and
     * SelfSerializable so the data can be written by one and read by the other. The reason for having the extra method
     * here is that it is inefficient in a hot spot area to have to wrap a ByteBuffer into an input or output stream for
     * every small read or write.
     *
     * @param buffer
     * 		The buffer to deserialize from, at the current position of that buffer. The buffers position should
     * 		not be changed other than it being incremented by the amount of data read.
     * @throws IOException
     * 		If there was a problem reading this classes data from the ByteBuffer
     */
    void deserialize(ByteBuffer buffer, int version) throws IOException;
}
