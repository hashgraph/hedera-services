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
import static java.util.stream.Collectors.toMap;

import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.tss.TssEncryptionKeys;
import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.state.tss.TssStatus;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.service.ReadableRosterStore;
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
     * @param rosterStore the roster store
     */
    default Optional<RosterKeys> consensusRosterKeys(
            @NonNull final Bytes sourceRosterHash,
            @NonNull final Bytes targetRosterHash,
            @NonNull final ReadableRosterStore rosterStore) {
        return anyWinningVoteFrom(sourceRosterHash, targetRosterHash, rosterStore)
                .map(vote -> {
                    final var tssMessages = getMessagesForTarget(vote.targetRosterHash());
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
     * If present, returns one of the winning votes from the given source roster hash for the keys of the target roster,
     * computing the given total weight and per-node weight for the source roster from the given roster store. There is
     * no guarantee of ordering between multiple winning votes.
     *
     * @param sourceRosterHash the source roster hash
     * @param targetRosterHash the target roster hash
     * @param rosterStore the roster store
     * @return the roster keys, if available
     */
    default Optional<TssVoteTransactionBody> anyWinningVoteFrom(
            @NonNull final Bytes sourceRosterHash,
            @NonNull final Bytes targetRosterHash,
            @NonNull final ReadableRosterStore rosterStore) {
        requireNonNull(sourceRosterHash);
        requireNonNull(targetRosterHash);
        requireNonNull(rosterStore);
        final long sourceRosterWeight;
        final LongUnaryOperator nodeWeightFn;
        if (Bytes.EMPTY.equals(sourceRosterHash)) {
            // For the genesis roster, we assume a source roster of equal size with equal unit weights
            sourceRosterWeight = requireNonNull(rosterStore.get(targetRosterHash))
                    .rosterEntries()
                    .size();
            nodeWeightFn = nodeId -> 1;
        } else {
            final var entries =
                    requireNonNull(rosterStore.get(sourceRosterHash)).rosterEntries();
            sourceRosterWeight = entries.stream().mapToLong(RosterEntry::weight).sum();
            final var weights = entries.stream().collect(toMap(RosterEntry::nodeId, RosterEntry::weight));
            nodeWeightFn = weights::get;
        }
        return anyWinningVoteFrom(sourceRosterHash, targetRosterHash, sourceRosterWeight, nodeWeightFn);
    }

    /**
     * If present, returns one of the winning votes from the given source roster hash for the keys of the target roster,
     * using the given total weight and per-node weight for the source roster. There is no guarantee of ordering between
     * multiple winning votes.
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
     * Get the list of Tss messages for the given roster hash.
     * @param rosterHash The roster hash to look up.
     * @return The list of Tss messages, or an empty list if not found.
     */
    List<TssMessageTransactionBody> getMessagesForTarget(@NonNull Bytes rosterHash);

    /**
     * Get the Tss encryption key transaction body for the given node ID.
     *
     * @param nodeID The node ID to look up.
     * @return The Tss encryption key transaction body, or null if not found.
     */
    @Nullable
    TssEncryptionKeys getTssEncryptionKeys(long nodeID);

    /**
     * Get the Tss status.
     * @return The Tss status.
     */
    @NonNull
    TssStatus getTssStatus();
}
