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

package com.swirlds.platform.system.transaction;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.io.SerializableWithKnownLength;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * A hashgraph transaction that consists of an array of bytes and a list of immutable {@link TransactionSignature}
 * objects. The list of signatures features controlled mutability with a thread-safe and atomic implementation. The
 * transaction internally uses a {@link ReadWriteLock} to provide atomic reads and writes to the underlying list of
 * signatures.
 * <p>
 * The contents provided by this class via {@link #getContents()} must never be mutated. Providing the direct (mutable)
 * reference improves performance by eliminating the need to create copies.
 * </p>
 */
public sealed interface Transaction extends SerializableWithKnownLength permits ConsensusTransaction {

    /**
     * Returns a direct (mutable) reference to the transaction contents/payload. Care must be
     * taken to never modify the array returned by this accessor. Modifying the array will result in undefined
     * behaviors.
     *
     * @return a direct reference to the transaction content/payload
     */
    byte[] getContents();

    /**
     * Get the size of the transaction
     *
     * @return the size of the transaction in the unit of byte
     */
    int getSize();

    /**
     * Internal use accessor that returns a flag indicating whether this is a system transaction.
     *
     * @return {@code true} if this is a system transaction; otherwise {@code false} if this is an application
     * 		transaction
     */
    boolean isSystem();

    /**
     * Returns the custom metadata object set via {@link #setMetadata(Object)}.
     *
     * @param <T>
     * 		the type of metadata object to return
     * @return the custom metadata object, or {@code null} if none was set
     * @throws ClassCastException
     * 		if the type of object supplied to {@link #setMetadata(Object)} is not compatible with {@code T}
     */
    <T> T getMetadata();

    /**
     * Attaches a custom object to this transaction meant to store metadata. This object is not serialized
     * and is kept in memory. It must be recalculated by the application after a restart.
     *
     * @param <T>
     * 		the object to attach
     */
    <T> void setMetadata(T metadata);
}
