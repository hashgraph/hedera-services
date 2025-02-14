// SPDX-License-Identifier: Apache-2.0
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
import java.util.List;
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
        final List<Bytes> transactions = event.getGossipEvent().transactions();
        final boolean isNewFormat = !transactions.isEmpty();
        final Hash hash = hashEvent(event.getEventCore(), event.getTransactions(), isNewFormat);
        event.setHash(hash);
        return event;
    }

    /**
     * Hashes the given {@link UnsignedEvent} and sets the hash on the event.
     *
     * @param event the event to hash
     */
    public void hashUnsignedEvent(@NonNull final UnsignedEvent event) {
        final Hash hash = hashEvent(event.getEventCore(), event.getTransactions(), true);
        event.setHash(hash);
    }

    /**
     * Hashes the given event and returns the hash.
     *
     * @param eventCore         the event to hash
     * @param transactions      the transactions to hash
     * @return the hash of the event
     */
    @NonNull
    private Hash hashEvent(
            @NonNull final EventCore eventCore,
            @NonNull final List<TransactionWrapper> transactions,
            final boolean isNewFormat) {
        try {
            EventCore.PROTOBUF.write(eventCore, eventStream);
            for (final TransactionWrapper transaction : transactions) {
                if (isNewFormat) {
                    transactionStream.writeBytes(Objects.requireNonNull(transaction.getApplicationTransaction()));
                } else {
                    EventTransaction.PROTOBUF.write(transaction.getTransaction(), transactionStream);
                }

                processTransactionHash(transaction);
            }
        } catch (final IOException e) {
            throw new RuntimeException("An exception occurred while trying to hash an event!", e);
        }

        return new Hash(eventDigest.digest(), DigestType.SHA_384);
    }

    private void processTransactionHash(final TransactionWrapper transaction) {
        final byte[] hash = transactionDigest.digest();
        transaction.setHash(Bytes.wrap(hash));
        eventStream.writeBytes(hash);
    }
}
