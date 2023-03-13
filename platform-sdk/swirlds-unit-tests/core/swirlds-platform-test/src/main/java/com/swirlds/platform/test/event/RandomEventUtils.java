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

package com.swirlds.platform.test.event;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.common.test.TransactionUtils;
import com.swirlds.platform.internal.EventImpl;
import java.time.Instant;
import java.util.Random;

public class RandomEventUtils {
    private static final int DEFAULT_MIN_TRANSACTION_NUMBER = 1;
    private static final int DEFAULT_MAX_TRANSACTION_NUMBER = 10;
    private static final int DEFAULT_MAX_NEXT_EVENT_MILLIS = 100;
    private static final boolean DEFAULT_FAKE_HASH = false;
    private static final boolean DEFAULT_REAL_EVENT = false;
    public static final Instant DEFAULT_FIRST_EVENT_TIME_CREATED = Instant.ofEpochMilli(1588771316678L);

    public static <T extends EventImpl> T randomEvent(
            final Random random, final int creatorId, final EventImpl selfParent, final EventImpl otherParent) {
        return randomEvent(random, creatorId, selfParent, otherParent, DEFAULT_FAKE_HASH, DEFAULT_REAL_EVENT);
    }

    public static <T extends EventImpl> T randomEvent(
            final Random random,
            final int creatorId,
            final EventImpl selfParent,
            final EventImpl otherParent,
            final boolean fakeHash,
            final boolean realEvent) {
        return randomEvent(
                random,
                creatorId,
                DEFAULT_FIRST_EVENT_TIME_CREATED,
                TransactionUtils.randomSwirldTransactions(
                        random, DEFAULT_MIN_TRANSACTION_NUMBER + random.nextInt(DEFAULT_MAX_TRANSACTION_NUMBER)),
                selfParent,
                otherParent,
                fakeHash,
                realEvent);
    }

    public static <T extends EventImpl> T randomEvent(
            final long seed,
            final int creatorId,
            final Instant firstTimeCreated,
            final SwirldTransaction[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        return randomEvent(new Random(seed), creatorId, firstTimeCreated, transactions, selfParent, otherParent);
    }

    public static <T extends EventImpl> T randomEvent(
            final Random random,
            final int creatorId,
            final Instant firstTimeCreated,
            final SwirldTransaction[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        return randomEvent(
                random,
                creatorId,
                firstTimeCreated,
                transactions,
                selfParent,
                otherParent,
                DEFAULT_FAKE_HASH,
                DEFAULT_REAL_EVENT);
    }

    public static <T extends EventImpl> T randomEvent(
            final Random random,
            final int creatorId,
            final Instant firstTimeCreated,
            final ConsensusTransactionImpl[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent,
            final boolean fakeHash,
            final boolean realEvent) {
        return (T) randomEvent(
                random,
                creatorId,
                firstTimeCreated,
                transactions,
                selfParent,
                otherParent,
                fakeHash,
                realEvent ? EventImpl::new : IndexedEvent::new);
    }

    @FunctionalInterface
    public interface EventConstructor<T extends EventImpl> {
        T construct(
                BaseEventHashedData hashedData,
                BaseEventUnhashedData unhashedData,
                EventImpl selfParent,
                EventImpl otherParent);
    }

    public static <T extends EventImpl> T randomEvent(
            final Random random,
            final int creatorId,
            final Instant firstTimeCreated,
            final ConsensusTransactionImpl[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent,
            final boolean fakeHash,
            final EventConstructor<T> constructor) {

        final BaseEventHashedData hashedData = randomEventHashedData(
                random, creatorId, firstTimeCreated, transactions, selfParent, otherParent, fakeHash);

        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);

        final BaseEventUnhashedData unhashedData =
                new BaseEventUnhashedData(otherParent != null ? otherParent.getCreatorId() : -1, sig);

        return constructor.construct(hashedData, unhashedData, selfParent, otherParent);
    }

    /**
     * Similar to randomEvent, but the timestamp used for the event's creation timestamp
     * is provided by an argument.
     */
    public static IndexedEvent randomEventWithTimestamp(
            final Random random,
            final int creatorId,
            final Instant timestamp,
            final ConsensusTransactionImpl[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent,
            final boolean fakeHash) {

        final BaseEventHashedData hashedData = randomEventHashedDataWithTimestamp(
                random, creatorId, timestamp, transactions, selfParent, otherParent, fakeHash);

        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);

        final BaseEventUnhashedData unhashedData =
                new BaseEventUnhashedData(otherParent != null ? otherParent.getCreatorId() : -1, sig);

        return new IndexedEvent(hashedData, unhashedData, selfParent, otherParent);
    }

    public static BaseEventHashedData randomEventHashedData(
            final long seed,
            final int creatorId,
            final Instant firstTimeCreated,
            final ConsensusTransactionImpl[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        return randomEventHashedData(
                new Random(seed), creatorId, firstTimeCreated, transactions, selfParent, otherParent);
    }

    public static BaseEventHashedData randomEventHashedData(
            final Random random,
            final int creatorId,
            final Instant firstTimeCreated,
            final ConsensusTransactionImpl[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent) {
        return randomEventHashedData(
                random, creatorId, firstTimeCreated, transactions, selfParent, otherParent, DEFAULT_FAKE_HASH);
    }

    public static BaseEventHashedData randomEventHashedData(
            final Random random,
            final int creatorId,
            final Instant firstTimeCreated,
            final ConsensusTransactionImpl[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent,
            final boolean fakeHash) {
        final BaseEventHashedData hashedData = new BaseEventHashedData(
                creatorId,
                selfParent != null ? selfParent.getGeneration() : -1,
                otherParent != null ? otherParent.getGeneration() : -1,
                selfParent != null ? selfParent.getBaseHash() : null,
                otherParent != null ? otherParent.getBaseHash() : null,
                selfParent != null
                        ? selfParent.getTimeCreated().plusMillis(1 + random.nextInt(DEFAULT_MAX_NEXT_EVENT_MILLIS))
                        : firstTimeCreated,
                transactions);

        if (fakeHash) {
            hashedData.setHash(RandomUtils.randomHash(random));
        } else {
            CryptographyHolder.get().digestSync(hashedData);
        }
        return hashedData;
    }

    /**
     * Similar to randomEventHashedData but where the timestamp provided to this
     * method is the timestamp used as the creation timestamp for the event.
     */
    public static BaseEventHashedData randomEventHashedDataWithTimestamp(
            final Random random,
            final int creatorId,
            final Instant timestamp,
            final ConsensusTransactionImpl[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent,
            final boolean fakeHash) {

        final BaseEventHashedData hashedData = new BaseEventHashedData(
                creatorId,
                selfParent != null ? selfParent.getGeneration() : -1,
                otherParent != null ? otherParent.getGeneration() : -1,
                selfParent != null ? selfParent.getBaseHash() : null,
                otherParent != null ? otherParent.getBaseHash() : null,
                timestamp,
                transactions);

        if (fakeHash) {
            hashedData.setHash(RandomUtils.randomHash(random));
        } else {
            CryptographyHolder.get().digestSync(hashedData);
        }
        return hashedData;
    }
}
