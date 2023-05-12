/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle;

import com.swirlds.common.crypto.SerializableHashable;
import com.swirlds.common.io.ExternalSelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.nio.file.Path;

/**
 * A Merkle Leaf has only data and does not have children.
 */
public interface MerkleLeaf extends MerkleNode, SerializableHashable, ExternalSelfSerializable {

    /**
     * {@inheritDoc}
     */
    @Override
    MerkleLeaf copy();

    /**
     * {@inheritDoc}
     */
    @Override
    default void serialize(final SerializableDataOutputStream out, final Path outputDirectory) throws IOException {
        // Default implementation ignores the provided directory. Override this method to utilize the directory.
        serialize(out);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default void deserialize(final SerializableDataInputStream in, final Path inputDirectory, final int version)
            throws IOException {
        // Default implementation ignores the provided directory. Override this method to utilize the directory.
        deserialize(in, version);
    }
}
