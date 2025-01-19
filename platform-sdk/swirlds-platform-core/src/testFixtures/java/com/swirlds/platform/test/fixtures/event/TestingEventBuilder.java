/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.system.events.EventConstants.MINIMUM_ROUND_CREATED;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.event.EventConsensusData;
import com.hedera.hapi.platform.event.EventDescriptor;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import com.swirlds.platform.system.events.UnsignedEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

/**
 * A builder for creating event instances for testing purposes.
 */
public class TestingEventBuilder {
    private static final Instant DEFAULT_TIMESTAMP = Instant.ofEpochMilli(1588771316678L);
    private static final SoftwareVersion DEFAULT_SOFTWARE_VERSION = new BasicSoftwareVersion(1);
    private static final NodeId DEFAULT_CREATOR_ID = NodeId.of(0);
    private static final int DEFAULT_APP_TRANSACTION_COUNT = 2;
    private static final int DEFAULT_SYSTEM_TRANSACTION_COUNT = 0;
    private static final int DEFAULT_TRANSACTION_SIZE = 4;

    private final Random random;

    /**
     * Creator ID to use.
     * <p>
     * If not set, defaults to the same creator ID as the self parent. If the self parent is not set, defaults to
     * {@link #DEFAULT_CREATOR_ID}.
     */
    private NodeId creatorId;

    /**
     * The time created of the event.
     * <p>
     * If not set, defaults to the time created of the self parent, plus a random number of milliseconds between
     * 1 and 99 inclusive. If the self parent is not set, defaults to {@link #DEFAULT_TIMESTAMP}.
     */
    private Instant timeCreated;

    /**
     * The number of app transactions an event should contain.
     * <p>
     * If not set, defaults to {@link #DEFAULT_APP_TRANSACTION_COUNT}.
     */
    private Integer appTransactionCount;

    /**
     * The number of system transactions an event should contain.
     * <p>
     * If not set, defaults to {@link #DEFAULT_SYSTEM_TRANSACTION_COUNT}.
     */
    private Integer systemTransactionCount;

    /**
     * The size in bytes of each transaction.
     * <p>
     * If not set, defaults to {@link #DEFAULT_TRANSACTION_SIZE}.
     */
    private Integer transactionSize;

    /**
     * The transactions to be contained in the event.
     * <p>
     * If not set, transactions will be auto generated, based on configured settings.
     */
    private List<Bytes> transactionBytes;

    private List<EventTransaction> transactions;

    /**
     * The self parent of the event.
     */
    private PlatformEvent selfParent;

    /**
     * The other parents of the event.
     */
    private List<PlatformEvent> otherParents;

    /**
     * Overrides the generation of the configured self parent.
     * <p>
     * Only relevant if the self parent is set.
     */
    private Long selfParentGenerationOverride;

    /**
     * Overrides the generation of the configured other parent.
     * <p>
     * Only relevant if the other parent is set.
     */
    private Long otherParentGenerationOverride;

    /**
     * Overrides the birth round of the configured self parent.
     * <p>
     * Only relevant if the self parent is set.
     */
    private Long selfParentBirthRoundOverride;

    /**
     * Overrides the birth round of the configured other parent.
     * <p>
     * Only relevant if the other parent is set.
     */
    private Long otherParentBirthRoundOverride;

    /**
     * The birth round of the event.
     * <p>
     * If not set, defaults to the maximum of the birth rounds of the self and other parents, plus a random number
     * between 0 and 2 inclusive.
     */
    private Long birthRound;

    /**
     * The software version of the event.
     * <p>
     * If not set, defaults to {@link #DEFAULT_SOFTWARE_VERSION}.
     */
    private SoftwareVersion softwareVersion;

    /**
     * The consensus timestamp of the event.
     * <p>
     * If consensus order is set, and consensus timestamp is not set, it will be a random timestamp.
     * <p>
     * If neither are set, defaults null, meaning this event will not be a consensus event.
     */
    private Instant consensusTimestamp;

    /**
     * The consensus order of the event.
     * <p>
     * If consensus timestamp is set, and consensus order is not set, it will be a random positive long.
     * <p>
     * If neither are set, defaults null, meaning this event will not be a consensus event.
     */
    private Long consensusOrder;

    /**
     * Constructor
     *
     * @param random a source of randomness
     */
    public TestingEventBuilder(@NonNull final Random random) {
        this.random = Objects.requireNonNull(random);
    }

    /**
     * Set the creator ID to use.
     * <p>
     * If not set, defaults to the same creator ID as the self parent. If the self parent is not set, defaults to
     * {@link #DEFAULT_CREATOR_ID}.
     *
     * @param creatorId the creator ID
     * @return this instance
     */
    public @NonNull TestingEventBuilder setCreatorId(@Nullable final NodeId creatorId) {
        this.creatorId = creatorId;
        return this;
    }

    /**
     * Set the software version of the event.
     * <p>
     * If not set, defaults to {@link #DEFAULT_SOFTWARE_VERSION}.
     *
     * @param softwareVersion the software version
     * @return this instance
     */
    public @NonNull TestingEventBuilder setSoftwareVersion(@Nullable final SemanticVersion softwareVersion) {
        this.softwareVersion = new BasicSoftwareVersion(softwareVersion.major());
        return this;
    }

    /**
     * Set the time created of an event.
     * <p>
     * If not set, defaults to the time created of the self parent, plus a random number of milliseconds between
     * 1 and 99 inclusive. If the self parent is not set, defaults to {@link #DEFAULT_TIMESTAMP}.
     *
     * @param timeCreated the time created
     * @return this instance
     */
    public @NonNull TestingEventBuilder setTimeCreated(@Nullable final Instant timeCreated) {
        this.timeCreated = timeCreated;
        return this;
    }

    /**
     * Set the number of app transactions an event should contain.
     * <p>
     * Throws an exception if transactions are explicitly set with {@link #setTransactions}.
     *
     * @param numberOfAppTransactions the number of app transactions
     * @return this instance
     */
    public @NonNull TestingEventBuilder setAppTransactionCount(final int numberOfAppTransactions) {
        if (transactions != null) {
            throw new IllegalStateException("Cannot set app transaction count when transactions are explicitly set");
        }

        this.appTransactionCount = numberOfAppTransactions;
        return this;
    }

    /**
     * Set the number of system transactions an event should contain.
     * <p>
     * Throws an exception if transactions are explicitly set with {@link #setTransactions}.
     *
     * @param numberOfSystemTransactions the number of system transactions
     * @return this instance
     */
    public @NonNull TestingEventBuilder setSystemTransactionCount(final int numberOfSystemTransactions) {
        if (transactions != null) {
            throw new IllegalStateException("Cannot set system transaction count when transactions are explicitly set");
        }

        this.systemTransactionCount = numberOfSystemTransactions;
        return this;
    }

    /**
     * Set the transaction size.
     * <p>
     * Throws an exception if transactions are explicitly set with {@link #setTransactions}.
     *
     * @param transactionSize the transaction size
     * @return this instance
     */
    public @NonNull TestingEventBuilder setTransactionSize(final int transactionSize) {
        if (transactions != null) {
            throw new IllegalStateException("Cannot set transaction size when transactions are explicitly set");
        }

        this.transactionSize = transactionSize;
        return this;
    }

    /**
     * Explicitly set the transactions of an event.
     * <p>
     * Throws an exception if app transaction count, system transaction count, or transaction size are set.
     *
     * @param transactions the transactions
     * @return this instance
     */
    public @NonNull TestingEventBuilder setTransactions(@Nullable final List<EventTransaction> transactions) {
        if (appTransactionCount != null || systemTransactionCount != null || transactionSize != null) {
            throw new IllegalStateException(
                    "Cannot set transactions when app transaction count, system transaction count, or transaction "
                            + "size are explicitly set");
        }

        this.transactions = transactions;
        return this;
    }

    public @NonNull TestingEventBuilder setTransactionBytes(@Nullable final List<Bytes> transactions) {
        if (appTransactionCount != null || systemTransactionCount != null || transactionSize != null) {
            throw new IllegalStateException(
                    "Cannot set transactions when app transaction count, system transaction count, or transaction "
                            + "size are explicitly set");
        }

        this.transactionBytes = transactions;
        return this;
    }

    /**
     * Convenience method to set the transactions of an event to wrap to a list of {@link EventTransaction}s
     *
     * @param transactions {@link OneOf<TransactionOneOfType>} transactions
     * @return this instance
     */
    public @NonNull TestingEventBuilder setOneOfTransactions(
            @Nullable final List<OneOf<TransactionOneOfType>> transactions) {
        Objects.requireNonNull(transactions, "transactions must not be null");
        final List<EventTransaction> eventTransactions =
                transactions.stream().map(EventTransaction::new).toList();
        return setTransactions(eventTransactions);
    }

    /**
     * Set the self-parent of an event.
     * <p>
     * If not set, a self parent will NOT be generated: the output event will have a null self parent.
     *
     * @param selfParent the self-parent
     * @return this instance
     */
    public @NonNull TestingEventBuilder setSelfParent(@Nullable final PlatformEvent selfParent) {
        this.selfParent = selfParent;
        return this;
    }

    /**
     * Set an other-parent of an event
     * <p>
     * If not set, no other-parent will be generated: the output event will have a no other-parents.
     *
     * @param otherParent the other-parent
     * @return this instance
     */
    public @NonNull TestingEventBuilder setOtherParent(@Nullable final PlatformEvent otherParent) {
        this.otherParents = otherParent == null ? null : List.of(otherParent);
        return this;
    }

    /**
     * Set a list of other-parents of an event
     * <p>
     * If not set, no other-parents will be generated: the output event will have a no other-parents.
     *
     * @param otherParents the other-parents
     * @return this instance
     */
    public @NonNull TestingEventBuilder setOtherParents(@NonNull final List<PlatformEvent> otherParents) {
        this.otherParents = otherParents;
        return this;
    }

    /**
     * Override the generation of the configured self parent.
     * <p>
     * Only relevant if the self parent is set.
     *
     * @param generation the generation to override with
     * @return this instance
     */
    public @NonNull TestingEventBuilder overrideSelfParentGeneration(final long generation) {
        this.selfParentGenerationOverride = generation;
        return this;
    }

    /**
     * Override the generation of the configured other parent.
     * <p>
     * Only relevant if the other parent is set.
     *
     * @param generation the generation to override with
     * @return this instance
     */
    public @NonNull TestingEventBuilder overrideOtherParentGeneration(final long generation) {
        this.otherParentGenerationOverride = generation;
        return this;
    }

    /**
     * Override the birth round of the configured self parent.
     * <p>
     * Only relevant if the self parent is set.
     *
     * @param birthRound the birth round to override with
     * @return this instance
     */
    public @NonNull TestingEventBuilder overrideSelfParentBirthRound(final long birthRound) {
        this.selfParentBirthRoundOverride = birthRound;
        return this;
    }

    /**
     * Override the birth round of the configured other parent.
     * <p>
     * Only relevant if the other parent is set.
     *
     * @param birthRound the birth round to override with
     * @return this instance
     */
    public @NonNull TestingEventBuilder overrideOtherParentBirthRound(final long birthRound) {
        this.otherParentBirthRoundOverride = birthRound;
        return this;
    }

    /**
     * Set the birth round of an event.
     * <p>
     * If not set, defaults to the maximum of the birth rounds of the self and other parents, plus a random number
     * between 0 and 2 inclusive.
     *
     * @param birthRound the birth round to set
     * @return this instance
     */
    public @NonNull TestingEventBuilder setBirthRound(final long birthRound) {
        this.birthRound = birthRound;
        return this;
    }

    /**
     * Set the consensus timestamp of an event.
     * <p>
     * If consensus order is set, and consensus timestamp is not set, it will be a random timestamp.
     * <p>
     * If neither are set, defaults null, meaning this event will not be a consensus event.
     *
     * @param consensusTimestamp the consensus timestamp
     * @return this instance
     */
    public @NonNull TestingEventBuilder setConsensusTimestamp(@Nullable final Instant consensusTimestamp) {
        this.consensusTimestamp = consensusTimestamp;
        return this;
    }

    /**
     * Set the consensus order of an event.
     * <p>
     * If consensus timestamp is set, and consensus order is not set, it will be a random positive long.
     * <p>
     * If neither are set, defaults null, meaning this event will not be a consensus event.
     *
     * @param consensusOrder the consensus order
     * @return this instance
     */
    public @NonNull TestingEventBuilder setConsensusOrder(@Nullable final Long consensusOrder) {
        this.consensusOrder = consensusOrder;
        return this;
    }

    /**
     * Generate transactions based on the settings provided.
     * <p>
     * Only utilized if the transactions are not set with {@link #setTransactions}.
     *
     * @return the generated transactions
     */
    @NonNull
    private List<Bytes> generateTransactions() {
        if (appTransactionCount == null) {
            appTransactionCount = DEFAULT_APP_TRANSACTION_COUNT;
        }

        if (systemTransactionCount == null) {
            systemTransactionCount = DEFAULT_SYSTEM_TRANSACTION_COUNT;
        }

        final List<Bytes> generatedTransactions = new ArrayList<>();

        if (transactionSize == null) {
            transactionSize = DEFAULT_TRANSACTION_SIZE;
        }

        for (int i = 0; i < appTransactionCount; ++i) {
            final byte[] bytes = new byte[transactionSize];
            random.nextBytes(bytes);
            generatedTransactions.add(Bytes.wrap(bytes));
        }

        for (int i = appTransactionCount; i < appTransactionCount + systemTransactionCount; ++i) {
            generatedTransactions.add(StateSignatureTransaction.PROTOBUF.toBytes(StateSignatureTransaction.newBuilder()
                    .round(random.nextLong(0, Long.MAX_VALUE))
                    .signature(RandomUtils.randomSignatureBytes(random))
                    .hash(RandomUtils.randomHashBytes(random))
                    .build()));
        }

        return generatedTransactions;
    }

    /**
     * Create an event descriptor from a parent event.
     *
     * @param parent             the parent event
     * @param generationOverride the generation to override with, or null if no override is necessary
     * @param birthRoundOverride the birth round to override with, or null if no override is necessary
     * @return the parent event descriptor
     */
    @Nullable
    private EventDescriptorWrapper createDescriptorFromParent(
            @Nullable final PlatformEvent parent,
            @Nullable final Long generationOverride,
            @Nullable final Long birthRoundOverride) {

        if (parent == null) {
            if (generationOverride != null) {
                throw new IllegalArgumentException("Cannot override generation on a parent that doesn't exist");
            }

            if (birthRoundOverride != null) {
                throw new IllegalArgumentException("Cannot override birth round on a parent that doesn't exist");
            }

            return null;
        }

        final long generation = generationOverride == null ? parent.getGeneration() : generationOverride;
        final long birthRound = birthRoundOverride == null ? parent.getBirthRound() : birthRoundOverride;

        return new EventDescriptorWrapper(new EventDescriptor(
                parent.getHash().getBytes(), parent.getCreatorId().id(), birthRound, generation));
    }

    /**
     * Build the event
     *
     * @return the new event
     */
    public @NonNull PlatformEvent build() {
        if (softwareVersion == null) {
            softwareVersion = DEFAULT_SOFTWARE_VERSION;
        }

        if (creatorId == null) {
            if (selfParent != null) {
                creatorId = selfParent.getCreatorId();
            } else {
                creatorId = DEFAULT_CREATOR_ID;
            }
        }

        final EventDescriptorWrapper selfParentDescriptor =
                createDescriptorFromParent(selfParent, selfParentGenerationOverride, selfParentBirthRoundOverride);
        final List<EventDescriptorWrapper> otherParentDescriptors = Stream.ofNullable(otherParents)
                .flatMap(List::stream)
                .map(parent -> createDescriptorFromParent(
                        parent, otherParentGenerationOverride, otherParentBirthRoundOverride))
                .toList();

        if (this.birthRound == null) {

            final long maxParentBirthRound = Stream.concat(
                            Stream.ofNullable(selfParent),
                            Stream.ofNullable(otherParents).flatMap(List::stream))
                    .mapToLong(PlatformEvent::getBirthRound)
                    .max()
                    .orElse(MINIMUM_ROUND_CREATED);

            // randomly add between 0 and 2 to max parent birth round
            birthRound = maxParentBirthRound + random.nextLong(0, 3);
        }

        if (timeCreated == null) {
            if (selfParent == null) {
                timeCreated = DEFAULT_TIMESTAMP;
            } else {
                // randomly add between 1 and 99 milliseconds to self parent time created
                timeCreated = selfParent.getTimeCreated().plusMillis(random.nextLong(1, 100));
            }
        }

        if (transactionBytes == null) {
            transactionBytes = generateTransactions();
        }

        final UnsignedEvent unsignedEvent = new UnsignedEvent(
                softwareVersion,
                creatorId,
                selfParentDescriptor,
                otherParentDescriptors,
                birthRound,
                timeCreated,
                transactionBytes);

        final byte[] signature = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(signature);

        final PlatformEvent platformEvent = new PlatformEvent(unsignedEvent, signature);

        platformEvent.setHash(RandomUtils.randomHash(random));

        if (consensusTimestamp != null || consensusOrder != null) {
            platformEvent.setConsensusData(new EventConsensusData.Builder()
                    .consensusTimestamp(HapiUtils.asTimestamp(
                            Optional.ofNullable(consensusTimestamp).orElse(RandomUtils.randomInstant(random))))
                    .consensusOrder(Optional.ofNullable(consensusOrder).orElse(random.nextLong(1, Long.MAX_VALUE)))
                    .build());
        }

        return platformEvent;
    }
}
