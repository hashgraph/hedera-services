/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Random;

/**
 * A builder for creating event instances for testing purposes.
 */
public class TestingEventBuilder {
    /**
     * default timestamp to use when none is set
     */
    private static final Instant DEFAULT_TIMESTAMP = Instant.ofEpochMilli(1588771316678L);
    /**
     * source of randomness
     */
    private final Random random;
    /**
     * creator ID to use
     */
    private NodeId creatorId;
    /**
     * the time created of an event
     */
    private Instant timeCreated;
    /**
     * the number of transactions an event should contain
     */
    private int numberOfAppTransactions;
    /**
     * the number of system transactions an event should contain
     */
    private int numberOfSystemTransactions;
    /**
     * the transaction size
     */
    private int transactionSize;
    /**
     * the transactions of an event
     */
    private ConsensusTransactionImpl[] transactions;

    private GossipEvent selfParent;
    private GossipEvent otherParent;
    /**
     * a fake generation to set for an event
     */
    private Long fakeGeneration;

    private TestingEventBuilder(@NonNull final Random random) {
        this.random = Objects.requireNonNull(random);
    }

    /**
     * @return a new instance of the builder with default settings
     */
    public static @NonNull TestingEventBuilder builder(@NonNull final Random random) {
        return new TestingEventBuilder(random).setDefaults();
    }

    /**
     * Set the builder to its default settings.
     *
     * @return this instance
     */
    public @NonNull TestingEventBuilder setDefaults() {
        creatorId = new NodeId(0);
        timeCreated = null;
        numberOfAppTransactions = 2;
        numberOfSystemTransactions = 0;
        transactionSize = 4;
        transactions = null;
        selfParent = null;
        otherParent = null;
        fakeGeneration = null;
        return this;
    }

    /**
     * Same as {@link #setDefaults()}.
     */
    public @NonNull TestingEventBuilder reset() {
        return setDefaults();
    }

    /**
     * Set the creator ID to use.
     *
     * @param creatorId the creator ID
     * @return this instance
     */
    public @NonNull TestingEventBuilder setCreatorId(final long creatorId) {
        this.creatorId = new NodeId(creatorId);
        return this;
    }

    /**
     * Set the creator ID to use. If set to null, the default creator ID will be used.
     *
     * @param creatorId the creator ID
     * @return this instance
     */
    public @NonNull TestingEventBuilder setCreatorId(@Nullable final NodeId creatorId) {
        this.creatorId = creatorId;
        return this;
    }

    /**
     * Set the time created of an event. If set to null, the default time created will be used.
     *
     * @param timeCreated the time created
     * @return this instance
     */
    public @NonNull TestingEventBuilder setTimeCreated(@Nullable final Instant timeCreated) {
        this.timeCreated = timeCreated;
        return this;
    }

    /**
     * Set the number of app transactions an event should contain. If
     * {@link #setTransactions(ConsensusTransactionImpl[])}
     * is called with a non-null value, this setting will be ignored.
     *
     * @param numberOfAppTransactions the number of app transactions
     * @return this instance
     */
    public @NonNull TestingEventBuilder setNumberOfAppTransactions(final int numberOfAppTransactions) {
        this.numberOfAppTransactions = numberOfAppTransactions;
        return this;
    }

    /**
     * Set the number of system transactions an event should contain. If
     * {@link #setTransactions(ConsensusTransactionImpl[])}
     * is called with a non-null value, this setting will be ignored.
     *
     * @param numberOfSystemTransactions the number of system transactions
     * @return this instance
     */
    public @NonNull TestingEventBuilder setNumberOfSystemTransactions(final int numberOfSystemTransactions) {
        this.numberOfSystemTransactions = numberOfSystemTransactions;
        return this;
    }

    /**
     * Set the transaction size. If {@link #setTransactions(ConsensusTransactionImpl[])} is called with a non-null
     * value, this setting will be ignored.
     *
     * @param transactionSize the transaction size
     * @return this instance
     */
    public @NonNull TestingEventBuilder setTransactionSize(final int transactionSize) {
        this.transactionSize = transactionSize;
        return this;
    }

    /**
     * Set the transactions of an event. If set to null, transactions will be generated based on setting set with
     * {@link #setNumberOfAppTransactions(int)} and {@link #setTransactionSize(int)}.
     *
     * @param transactions the transactions
     * @return this instance
     */
    public @NonNull TestingEventBuilder setTransactions(@Nullable final ConsensusTransactionImpl[] transactions) {
        this.transactions = transactions;
        return this;
    }

    /**
     * Set the self-parent of an event. If set to null, no self-parent will be used unless a generation is set, in which
     * case, a self-parent descriptor will be created with the given generation.
     *
     * @param selfParent the self-parent
     * @return this instance
     */
    public @NonNull TestingEventBuilder setSelfParent(@Nullable final GossipEvent selfParent) {
        this.selfParent = selfParent;
        return this;
    }

    /**
     * Set the other-parent of an event. If set to null, no other-parent will be used unless a generation is set, in
     * which case, an other-parent descriptor will be created with the given generation.
     *
     * @param otherParent the other-parent
     * @return this instance
     */
    public @NonNull TestingEventBuilder setOtherParent(@Nullable final GossipEvent otherParent) {
        this.otherParent = otherParent;
        return this;
    }

    /**
     * Set a generation to set for an event. Since the generation is based on the parents generation, this functionality
     * creates parent descriptors for an event to get the desired generation. It does not work in conjunction with
     * setting the parents for an event. If parents are set, then the generation will be based on the parents provided.
     *
     * @param generation the generation
     * @return this instance
     */
    public @NonNull TestingEventBuilder setGeneration(final Long generation) {
        fakeGeneration = generation;
        return this;
    }

    /**
     * Get the time created of the self-parent. If the self-parent is null, the default timestamp will be returned.
     *
     * @return the time created instant of the self-parent
     */
    private @NonNull Instant getSelfParentTimeCreated() {
        return selfParent == null
                ? DEFAULT_TIMESTAMP
                : selfParent.getHashedData().getTimeCreated();
    }

    /**
     * Build a GossipEvent instance.
     *
     * @return the GossipEvent instance
     */
    public @NonNull GossipEvent build() {
        final ConsensusTransactionImpl[] tr;
        if (transactions == null) {
            tr = new ConsensusTransactionImpl[numberOfAppTransactions + numberOfSystemTransactions];
            for (int i = 0; i < numberOfAppTransactions; ++i) {
                final byte[] bytes = new byte[transactionSize];
                random.nextBytes(bytes);
                tr[i] = new SwirldTransaction(bytes);
            }
            for (int i = numberOfAppTransactions; i < numberOfAppTransactions + numberOfSystemTransactions; ++i) {
                tr[i] = new StateSignatureTransaction(
                        random.nextLong(0, Long.MAX_VALUE),
                        RandomUtils.randomSignature(random),
                        RandomUtils.randomHash(random));
            }
        } else {
            tr = transactions;
        }

        final long selfParentGen = fakeGeneration != null
                ? fakeGeneration - 1
                : selfParent != null ? selfParent.getGeneration() : EventConstants.GENERATION_UNDEFINED;
        final long otherParentGen = fakeGeneration != null
                ? fakeGeneration - 1
                : otherParent != null ? otherParent.getGeneration() : EventConstants.GENERATION_UNDEFINED;

        final EventDescriptor selfParentDescriptor = selfParent != null
                ? new EventDescriptor(
                        selfParent.getHashedData().getHash(),
                        creatorId,
                        selfParentGen,
                        EventConstants.BIRTH_ROUND_UNDEFINED)
                : selfParentGen > EventConstants.GENERATION_UNDEFINED
                        ? new EventDescriptor(
                                RandomUtils.randomHash(random),
                                creatorId,
                                selfParentGen,
                                EventConstants.BIRTH_ROUND_UNDEFINED)
                        : null;
        final EventDescriptor otherParentDescriptor = otherParent != null
                ? new EventDescriptor(
                        otherParent.getHashedData().getHash(),
                        otherParent.getHashedData().getCreatorId(),
                        otherParentGen,
                        EventConstants.BIRTH_ROUND_UNDEFINED)
                : otherParentGen > EventConstants.GENERATION_UNDEFINED
                        ? new EventDescriptor(
                                RandomUtils.randomHash(random),
                                creatorId,
                                otherParentGen,
                                EventConstants.BIRTH_ROUND_UNDEFINED)
                        : null;
        final BaseEventHashedData hashedData = new BaseEventHashedData(
                new BasicSoftwareVersion(1),
                creatorId,
                selfParentDescriptor,
                otherParentDescriptor == null
                        ? Collections.emptyList()
                        : Collections.singletonList(otherParentDescriptor),
                EventConstants.BIRTH_ROUND_UNDEFINED,
                timeCreated == null ? getSelfParentTimeCreated().plusMillis(1 + creatorId.id()) : timeCreated,
                tr);

        hashedData.setHash(RandomUtils.randomHash(random));

        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                otherParent == null ? null : otherParent.getHashedData().getCreatorId(), sig);
        final GossipEvent gossipEvent = new GossipEvent(hashedData, unhashedData);
        gossipEvent.buildDescriptor();
        return gossipEvent;
    }
}
