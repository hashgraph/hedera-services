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
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.events.UnsignedEvent;
import com.swirlds.platform.system.transaction.TransactionWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Hashes the PBJ representation of an event. This hasher double hashes each transaction in order to allow redaction of
 * transactions without invalidating the event hash.
 */
public class PbjStreamHasher implements EventHasher, UnsignedEventHasher {

    /** The hashing stream for the event. */
    private final MessageDigest eventDigest = DigestType.SHA_384.buildDigest();

    final WritableSequentialData eventStream = new WritableStreamingData(new HashingOutputStream(eventDigest));
    /** The hashing stream for the transactions. */
    private final MessageDigest transactionDigest = DigestType.SHA_384.buildDigest();

    final WritableSequentialData transactionStream =
            new WritableStreamingData(new HashingOutputStream(transactionDigest));

    @Override
    @NonNull
    public PlatformEvent hashEvent(@NonNull final PlatformEvent event) {
        Objects.requireNonNull(event);
        hashUnsignedEvent(event.getUnsignedEvent());
        event.setHash(event.getUnsignedEvent().getHash());
        return event;
    }

    /**
     * Hashes the given {@link UnsignedEvent} and sets the hash on the event.
     *
     * @param event the event to hash
     */
    public void hashUnsignedEvent(@NonNull final UnsignedEvent event) {
        try {
            EventCore.PROTOBUF.write(event.getEventCore(), eventStream);
            for (final TransactionWrapper transaction : event.getTransactions()) {
                EventTransaction.PROTOBUF.write(transaction.getTransaction(), transactionStream);
                byte[] hash = transactionDigest.digest();
                transaction.setHash(Bytes.wrap(hash));
                eventStream.writeBytes(hash);
            }
        } catch (final IOException e) {
            throw new RuntimeException("An exception occurred while trying to hash an event!", e);
        }

        event.setHash(new Hash(eventDigest.digest(), DigestType.SHA_384));
    }
}
