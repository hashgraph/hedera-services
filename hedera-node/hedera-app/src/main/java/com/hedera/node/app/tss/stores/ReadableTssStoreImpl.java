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

package com.hedera.node.app.tss.stores;

import static com.hedera.node.app.tss.handlers.TssVoteHandler.hasMetThreshold;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_MESSAGE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0560TssBaseSchema.TSS_VOTE_MAP_KEY;
import static com.hedera.node.app.tss.schemas.V0570TssBaseSchema.TSS_ENCRYPTION_KEY_MAP_KEY;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterators.spliterator;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssEncryptionKeyTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.LongUnaryOperator;

/**
 * Provides read-only access to the TSS base store.
 */
public class ReadableTssStoreImpl implements ReadableTssStore {
    /**
     * The underlying data storage class that holds the airdrop data.
     */
    private final ReadableKVState<TssMessageMapKey, TssMessageTransactionBody> readableTssMessageState;

    private final ReadableKVState<TssVoteMapKey, TssVoteTransactionBody> readableTssVoteState;

    private final ReadableKVState<EntityNumber, TssEncryptionKeyTransactionBody> readableTssEncryptionKeyState;

    /**
     * Create a new {@link ReadableTssStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableTssStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.readableTssMessageState = states.get(TSS_MESSAGE_MAP_KEY);
        this.readableTssVoteState = states.get(TSS_VOTE_MAP_KEY);
        this.readableTssEncryptionKeyState = states.get(TSS_ENCRYPTION_KEY_MAP_KEY);
    }

    @Override
    public Optional<TssVoteTransactionBody> anyWinningVoteFrom(
            @NonNull final Bytes sourceRosterHash,
            @NonNull final Bytes targetRosterHash,
            final long sourceRosterWeight,
            @NonNull final LongUnaryOperator sourceRosterWeightFn) {
        requireNonNull(sourceRosterHash);
        requireNonNull(targetRosterHash);
        requireNonNull(sourceRosterWeightFn);
        return stream(spliterator(readableTssVoteState.keys(), readableTssVoteState.size(), NONNULL), false)
                .filter(key -> targetRosterHash.equals(key.rosterHash()))
                .map(key -> new WeightedVote(
                        sourceRosterWeightFn.applyAsLong(key.nodeId()), requireNonNull(readableTssVoteState.get(key))))
                .filter(vote -> sourceRosterHash.equals(vote.vote().sourceRosterHash()))
                .collect(groupingBy(WeightedVote::tssVote, toList()))
                .values()
                .stream()
                .filter(weightedVotes -> hasMetThreshold(
                        weightedVotes.stream().mapToLong(WeightedVote::weight).sum(), sourceRosterWeight))
                .findAny()
                .map(weightedVotes -> weightedVotes.getFirst().vote());
    }

    private record WeightedVote(long weight, @NonNull TssVoteTransactionBody vote) {
        public WeightedVote {
            requireNonNull(vote);
        }

        public Bytes tssVote() {
            return vote.tssVote();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TssMessageTransactionBody getMessage(@NonNull final TssMessageMapKey tssMessageKey) {
        return readableTssMessageState.get(tssMessageKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(@NonNull final TssMessageMapKey tssMessageKey) {
        return readableTssMessageState.contains(tssMessageKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TssVoteTransactionBody getVote(@NonNull final TssVoteMapKey tssVoteKey) {
        return readableTssVoteState.get(tssVoteKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean exists(@NonNull final TssVoteMapKey tssVoteKey) {
        return readableTssVoteState.contains(tssVoteKey);
    }

    @Override
    public long messageStateSize() {
        return readableTssMessageState.size();
    }

    @Override
    public List<TssMessageTransactionBody> getTssMessageBodies(final Bytes rosterHash) {
        final List<TssMessageTransactionBody> tssMessages = new ArrayList<>();
        readableTssMessageState.keys().forEachRemaining(key -> {
            if (key.rosterHash().equals(rosterHash)) {
                tssMessages.add(readableTssMessageState.get(key));
            }
        });
        return tssMessages;
    }

    @Override
    public List<TssVoteTransactionBody> getTssVoteBodies(final Bytes rosterHash) {
        final List<TssVoteTransactionBody> tssMessages = new ArrayList<>();
        readableTssVoteState.keys().forEachRemaining(key -> {
            if (key.rosterHash().equals(rosterHash)) {
                tssMessages.add(readableTssVoteState.get(key));
            }
        });
        return tssMessages;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TssEncryptionKeyTransactionBody getTssEncryptionKey(long nodeID) {
        return readableTssEncryptionKeyState.get(
                EntityNumber.newBuilder().number(nodeID).build());
    }
}
