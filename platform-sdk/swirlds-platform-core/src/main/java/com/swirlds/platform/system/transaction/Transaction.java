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

import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.platform.util.TransactionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.locks.ReadWriteLock;

/**
 * A hashgraph transaction that consists of an array of bytes and a list of immutable {@link TransactionSignature}
 * objects. The list of signatures features controlled mutability with a thread-safe and atomic implementation. The
 * transaction internally uses a {@link ReadWriteLock} to provide atomic reads and writes to the underlying list of
 * signatures.
 */
public sealed interface Transaction permits ConsensusTransaction {

    /**
     * Returns the transaction as a PBJ record
     * @return the transaction
     */
    @NonNull
    @Deprecated
    EventTransaction getTransaction();

    /**
     * A convenience method for retrieving the application transaction {@link Bytes} object. Before calling this method,
     * ensure that the transaction is not a system transaction by calling {@link #isSystem()}.
     *
     * @return the application transaction Bytes or {@code Bytes.EMPTY} if the transaction is a system transaction
     */
    @Deprecated
    default @NonNull Bytes getApplicationTransaction() {
        return !isSystem() ? getTransaction().transaction().as() : Bytes.EMPTY;
    }

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
    @Deprecated
    default boolean isSystem() {
        return TransactionUtils.isSystemTransaction(getTransaction());
    }

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
