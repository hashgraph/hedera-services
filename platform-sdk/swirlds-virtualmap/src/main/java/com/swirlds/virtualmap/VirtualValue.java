/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A {@link VirtualValue} is a "virtual" value, and is part of the API for the {@code VirtualMap}.
 * {@code VirtualMap}s, by their nature, need both keys and values which are serializable
 * and {@link FastCopyable}. To enhance performance, serialization methods that work with
 * {@link ByteBuffer} are required on a VValue.
 */
public interface VirtualValue extends SelfSerializable, FastCopyable {

    @Override
    VirtualValue copy();

    /**
     * Gets a copy of this Value which is entirely read-only.
     *
     * @return A non-null copy that is read-only. Can be a view rather than a copy.
     */
    VirtualValue asReadOnly();

    /**
     * Serialize this value into the specified buffer. The buffer will be pre-sized and
     * prepared. The specific {@link VirtualDataSource}
     * implementation you use will require information on the number of bytes per
     * value so that it can prepare such a buffer ahead of time.
     *
     * @param buffer
     * 		The buffer to fill. Will never be null.
     * @throws IOException
     * 		If an I/O exception happens during serialization.
     */
    void serialize(final ByteBuffer buffer) throws IOException;

    void deserialize(final ByteBuffer buffer, final int version) throws IOException;
}
