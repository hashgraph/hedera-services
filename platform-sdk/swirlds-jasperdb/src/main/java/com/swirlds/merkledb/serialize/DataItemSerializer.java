/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import java.nio.ByteBuffer;

public interface DataItemSerializer<D> extends BaseSerializer<D> {

    /**
     * Get the number of bytes used for data item header
     *
     * <p>Deprecation note: this method is only used by MerkleDb, when it deserializes data
     * from JDB format. This format will be eventually removed.
     *
     * @return size of header in bytes
     */
    @Deprecated
    int getHeaderSize();

    /**
     * Deserialize data item header from the given byte buffer
     *
     * <p>Deprecation note: this method is only used by MerkleDb, when it deserializes data
     * from JDB format. This format will be eventually removed.
     *
     * @param buffer Buffer to read from
     * @return The read header
     */
    @Deprecated
    DataItemHeader deserializeHeader(ByteBuffer buffer);
}
