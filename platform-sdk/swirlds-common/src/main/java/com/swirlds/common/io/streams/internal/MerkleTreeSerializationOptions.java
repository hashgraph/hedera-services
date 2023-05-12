/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.streams.internal;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

/**
 * @deprecated this class fragment is present for migration purposes only
 */
@Deprecated(forRemoval = true)
public class MerkleTreeSerializationOptions implements SelfSerializable {
    private static final long CLASS_ID = 0x76a4b529cfba0bccL;
    private static final int CLASS_VERSION = 1;

    public static final int MAX_LENGTH_BYTES = 128;

    public MerkleTreeSerializationOptions() {}

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        // Read and discard the data for this class
        in.readByteArray(MAX_LENGTH_BYTES);
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        throw new UnsupportedOperationException("this class is deprecated and should not be used");
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }
}
