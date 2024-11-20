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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssEncryptionKeyTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.LongUnaryOperator;
import java.util.stream.IntStream;

public interface ReadableTssStore {
    /**
     * The selected TSS messages and implied ledger id for some roster.
     * @param tssMessages the selected TSS messages
     * @param ledgerId the implied ledger id
     */
    record RosterKeys(@NonNull List<TssMessageTransactionBody> tssMessages, @NonNull Bytes ledgerId) {
        public RosterKeys {
            requireNonNull(tssMessages);
            requireNonNull(ledgerId);
        }
    }

    /**
     * If available, returns the roster keys that the given source roster voted to assign to the given target roster.
     *
     * @param sourceRosterHash the source roster hash
     * @param targetRosterHash the target roster hash
     * @param sourceRosterWeight the total weight of the source roster
     * @param nodeWeightFn a function that returns the weight of a node in the source roster given its id
     */
    default Optional<RosterKeys> consensusRosterKeys(
            @NonNull final Bytes sourceRosterHash,
            @NonNull final Bytes targetRosterHash,
            final long sourceRosterWeight,
            @NonNull final LongUnaryOperator nodeWeightFn) {
        return anyWinningVoteFrom(sourceRosterHash, targetRosterHash, sourceRosterWeight, nodeWeightFn)
                .map(vote -> {
                    final var tssMessages = getTssMessageBodies(vote.targetRosterHash());
                    final var selections = BitSet.valueOf(vote.tssVote().toByteArray());
                    final var selectedMessages = IntStream.range(0, tssMessages.size())
                            .filter(selections::get)
                            .sorted()
                            .mapToObj(tssMessages::get)
                            .toList();
                    return new RosterKeys(selectedMessages, vote.ledgerId());
                });
    }

    /**
     * If present, returns one of the winning votes for the given source roster hash and total weight. There is no
     * guarantee of ordering between multiple winning votes.
     *
     * @param sourceRosterHash the source roster hash the vote must be from
     * @param targetRosterHash the target roster hash the vote must be for
     * @param sourceRosterWeight the total weight of the source the vote must be from
     * @param sourceRosterWeightFn a function that returns the weight of a node in the source roster given its id
     * @return a winning vote, if present
     */
    Optional<TssVoteTransactionBody> anyWinningVoteFrom(
            @NonNull Bytes sourceRosterHash,
            @NonNull Bytes targetRosterHash,
            long sourceRosterWeight,
            @NonNull LongUnaryOperator sourceRosterWeightFn);

    /**
     * Get the TSS message for the given key.
     *
     * @param TssMessageMapKey The key to look up.
     * @return The TSS message, or null if not found.
     */
    TssMessageTransactionBody getMessage(@NonNull TssMessageMapKey TssMessageMapKey);

    /**
     * Check if a TSS message exists for the given key.
     *
     * @param tssMessageMapKey The key to check.
     * @return True if a TSS message exists for the given key, false otherwise.
     */
    boolean exists(@NonNull TssMessageMapKey tssMessageMapKey);

    /**
     * Get the TSS vote for the given key.
     *
     * @param tssVoteMapKey The key to look up.
     * @return The TSS vote, or null if not found.
     */
    TssVoteTransactionBody getVote(@NonNull TssVoteMapKey tssVoteMapKey);

    /**
     * Check if a TSS vote exists for the given key.
     *
     * @param tssVoteMapKey The key to check.
     * @return True if a TSS vote exists for the given key, false otherwise.
     */
    boolean exists(@NonNull TssVoteMapKey tssVoteMapKey);

    /**
     * Get the number of entries in the TSS message state.
     *
     * @return The number of entries in the tss message state.
     */
    long messageStateSize();

    /**
     * Get the list of Tss messages for the given roster hash.
     * @param rosterHash The roster hash to look up.
     * @return The list of Tss messages, or an empty list if not found.
     */
    List<TssMessageTransactionBody> getTssMessageBodies(Bytes rosterHash);

    /**
     * Get the list of Tss votes for the given roster hash.
     * @param rosterHash The roster hash to look up.
     * @return The list of Tss votes, or an empty list if not found.
     */
    List<TssVoteTransactionBody> getTssVoteBodies(Bytes rosterHash);

    /**
     * Get the Tss encryption key transaction body for the given node ID.
     * @param nodeID The node ID to look up.
     * @return The Tss encryption key transaction body, or null if not found.
     */
    @Nullable
    TssEncryptionKeyTransactionBody getTssEncryptionKey(final long nodeID);
}
