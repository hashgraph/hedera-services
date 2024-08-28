/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.hashing;

import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.events.UnsignedEvent;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * Hashes the PBJ representation of an event. This hasher double hashes each transaction in order to allow redaction of
 * transactions without invalidating the event hash.
 */
public class PbjBytesHasher implements EventHasher, UnsignedEventHasher {

    /** The digest for the event. */
    private final MessageDigest eventDigest = DigestType.SHA_384.buildDigest();
    /** The digest for the transactions. */
    private final MessageDigest transactionDigest = DigestType.SHA_384.buildDigest();

    @Override
    @NonNull
    public PlatformEvent hashEvent(@NonNull final PlatformEvent event) {
        Objects.requireNonNull(event);

        final Hash hash = hashEvent(event.getEventCore(), event.getTransactions());
        event.setHash(hash);

        return event;
    }

    /**
     * Hashes the given {@link UnsignedEvent} and sets the hash on the event.
     *
     * @param event the event to hash
     */
    public void hashUnsignedEvent(@NonNull final UnsignedEvent event) {
        final Hash hash = hashEvent(event.getEventCore(), event.getTransactions());
        event.setHash(hash);
    }

    @NonNull
    public Hash hashEvent(@NonNull final EventCore eventCore, @NonNull final List<TransactionWrapper> transactions) {
        EventCore.PROTOBUF.toBytes(eventCore).writeTo(eventDigest);
        transactions.forEach(transactionWrapper -> {
            EventTransaction.PROTOBUF
                    .toBytes(transactionWrapper.getTransaction())
                    .writeTo(transactionDigest);
            byte[] transactionHash = transactionDigest.digest();
            transactionWrapper.setHash(Bytes.wrap(transactionHash));
            eventDigest.update(transactionHash);
        });

        return new Hash(eventDigest.digest(), DigestType.SHA_384);
    }
}
