/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.event;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.TransactionUtils;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Random;

public class RandomEventUtils {
    private static final int DEFAULT_MIN_TRANSACTION_NUMBER = 1;
    private static final int DEFAULT_MAX_TRANSACTION_NUMBER = 10;
    private static final int DEFAULT_MAX_NEXT_EVENT_MILLIS = 100;
    private static final boolean DEFAULT_FAKE_HASH = false;
    private static final boolean DEFAULT_REAL_EVENT = false;
    public static final Instant DEFAULT_FIRST_EVENT_TIME_CREATED = Instant.ofEpochMilli(1588771316678L);

    public static <T extends EventImpl> T randomEvent(
            final Random random, final NodeId creatorId, final EventImpl selfParent, final EventImpl otherParent) {
        return randomEvent(random, creatorId, selfParent, otherParent, DEFAULT_FAKE_HASH, DEFAULT_REAL_EVENT);
    }

    public static <T extends EventImpl> T randomEvent(
            @NonNull final Random random,
            @NonNull final NodeId creatorId,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent,
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
            @NonNull final NodeId creatorId,
            @NonNull final Instant firstTimeCreated,
            @Nullable final SwirldTransaction[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent) {
        return randomEvent(new Random(seed), creatorId, firstTimeCreated, transactions, selfParent, otherParent);
    }

    public static <T extends EventImpl> T randomEvent(
            @NonNull final Random random,
            @NonNull final NodeId creatorId,
            @NonNull final Instant firstTimeCreated,
            @Nullable final SwirldTransaction[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent) {
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
            @NonNull final Random random,
            @NonNull final NodeId creatorId,
            @NonNull final Instant firstTimeCreated,
            @Nullable final ConsensusTransactionImpl[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent,
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
            @NonNull final Random random,
            @NonNull final NodeId creatorId,
            @NonNull final Instant firstTimeCreated,
            @Nullable final ConsensusTransactionImpl[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent,
            final boolean fakeHash,
            @NonNull final EventConstructor<T> constructor) {
        Objects.requireNonNull(random, "random must not be null");
        Objects.requireNonNull(creatorId, "creatorId must not be null");
        Objects.requireNonNull(firstTimeCreated, "firstTimeCreated must not be null");
        Objects.requireNonNull(constructor, "constructor must not be null");

        final BaseEventHashedData hashedData = randomEventHashedData(
                random, creatorId, firstTimeCreated, transactions, selfParent, otherParent, fakeHash);

        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                otherParent != null ? otherParent.getCreatorId() : NodeId.UNDEFINED_NODE_ID, sig);

        return constructor.construct(hashedData, unhashedData, selfParent, otherParent);
    }

    /**
     * Similar to randomEvent, but the timestamp used for the event's creation timestamp
     * is provided by an argument.
     */
    public static IndexedEvent randomEventWithTimestamp(
            final Random random,
            final NodeId creatorId,
            final Instant timestamp,
            final ConsensusTransactionImpl[] transactions,
            final EventImpl selfParent,
            final EventImpl otherParent,
            final boolean fakeHash) {

        final BaseEventHashedData hashedData = randomEventHashedDataWithTimestamp(
                random, creatorId, timestamp, transactions, selfParent, otherParent, fakeHash);

        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                otherParent != null ? otherParent.getCreatorId() : NodeId.UNDEFINED_NODE_ID, sig);

        return new IndexedEvent(hashedData, unhashedData, selfParent, otherParent);
    }

    public static BaseEventHashedData randomEventHashedData(
            final long seed,
            @NonNull final NodeId creatorId,
            @NonNull final Instant firstTimeCreated,
            @Nullable final ConsensusTransactionImpl[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent) {
        return randomEventHashedData(
                new Random(seed), creatorId, firstTimeCreated, transactions, selfParent, otherParent);
    }

    public static BaseEventHashedData randomEventHashedData(
            @NonNull final Random random,
            @NonNull final NodeId creatorId,
            @NonNull final Instant firstTimeCreated,
            @Nullable final ConsensusTransactionImpl[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent) {
        return randomEventHashedData(
                random, creatorId, firstTimeCreated, transactions, selfParent, otherParent, DEFAULT_FAKE_HASH);
    }

    public static BaseEventHashedData randomEventHashedData(
            @NonNull final Random random,
            @NonNull final NodeId creatorId,
            @NonNull final Instant firstTimeCreated,
            @Nullable final ConsensusTransactionImpl[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent,
            final boolean fakeHash) {

        final EventDescriptor selfDescriptor = selfParent == null
                ? null
                : new EventDescriptor(
                        selfParent.getHash(),
                        selfParent.getCreatorId(),
                        selfParent.getGeneration(),
                        EventConstants.BIRTH_ROUND_UNDEFINED);
        final EventDescriptor otherDescriptor = otherParent == null
                ? null
                : new EventDescriptor(
                        otherParent.getHash(),
                        otherParent.getCreatorId(),
                        otherParent.getGeneration(),
                        EventConstants.BIRTH_ROUND_UNDEFINED);

        final BaseEventHashedData hashedData = new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                creatorId,
                selfDescriptor,
                otherDescriptor == null ? Collections.emptyList() : Collections.singletonList(otherDescriptor),
                EventConstants.BIRTH_ROUND_UNDEFINED,
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
            @NonNull final Random random,
            @NonNull final NodeId creatorId,
            @NonNull final Instant timestamp,
            @Nullable final ConsensusTransactionImpl[] transactions,
            @Nullable final EventImpl selfParent,
            @Nullable final EventImpl otherParent,
            final boolean fakeHash) {

        final EventDescriptor selfDescriptor = (selfParent == null || selfParent.getBaseHash() == null)
                ? null
                : new EventDescriptor(
                        selfParent.getBaseHash(),
                        selfParent.getCreatorId(),
                        selfParent.getGeneration(),
                        EventConstants.BIRTH_ROUND_UNDEFINED);
        final EventDescriptor otherDescriptor = (otherParent == null || otherParent.getBaseHash() == null)
                ? null
                : new EventDescriptor(
                        otherParent.getBaseHash(),
                        otherParent.getCreatorId(),
                        otherParent.getGeneration(),
                        EventConstants.BIRTH_ROUND_UNDEFINED);

        final BaseEventHashedData hashedData = new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                creatorId,
                selfDescriptor,
                otherDescriptor == null ? Collections.emptyList() : Collections.singletonList(otherDescriptor),
                EventConstants.BIRTH_ROUND_UNDEFINED,
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
