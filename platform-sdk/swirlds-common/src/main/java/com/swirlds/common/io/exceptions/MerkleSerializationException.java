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

package com.swirlds.common.io.exceptions;

import static com.swirlds.common.constructable.ClassIdFormatter.versionedClassIdString;

import com.swirlds.common.merkle.MerkleNode;
import java.io.IOException;

/**
 * This exception can be thrown during serialization or deserialization of a merkle tree.
 */
public class MerkleSerializationException extends IOException {

    public MerkleSerializationException() {}

    public MerkleSerializationException(final String message) {
        super(message);
    }

    /**
     * Throw an exception for a particular node.
     *
     * @param message
     * 		the message to be included in the exception
     * @param node
     * 		the node that caused the exception
     */
    public MerkleSerializationException(final String message, final MerkleNode node) {
        super(message + " [" + versionedClassIdString(node) + "]");
    }

    public MerkleSerializationException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public MerkleSerializationException(final Throwable cause) {
        super(cause);
    }
}
