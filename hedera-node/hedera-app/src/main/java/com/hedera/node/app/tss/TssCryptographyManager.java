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

package com.hedera.node.app.tss;

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.node.app.roster.ReadableRosterStore;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.api.TssPublicShare;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.stores.WritableTssBaseStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.platform.NodeId;

import javax.inject.Singleton;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * This is yet to be implemented
 */
@Singleton
public class TssCryptographyManager {
    private static final int NUM_MAX_SHARES_PER_NODE = 10;
    private Map<Bytes, List<TssMessage>> tssMessages = new LinkedHashMap<>();
    Map<Bytes, List<TssVoteTransactionBody>> tssVotes = new LinkedHashMap<>();
    Map<Bytes, BitSet> tssVoteBitSet = new LinkedHashMap<>();
    Map<Bytes, PairingPublicKey> ledgerIds = new LinkedHashMap<>();
    Map<Bytes, Map<Long, Integer>> nodeShareCounts = new LinkedHashMap<>();
    Map<Bytes, List<TssPublicShare>> publicShares = new LinkedHashMap<>();
    Map<Bytes, List<TssPrivateShare>> privateShares = new LinkedHashMap<>();

    private Set<Bytes> votingClosed = new LinkedHashSet();
    private boolean createNewLedgerId = false;
    private NodeId nodeId;

    private final TssLibrary tssLibrary;
    private TssParticipantDirectory tssParticipantDirectory;

    private AppContext.LedgerSigner ledgerSigner;

    public TssCryptographyManager(NodeId nodeId,
                                  TssLibrary tssLibrary,
                                  TssParticipantDirectory tssParticipantDirectory,
                                  AppContext.LedgerSigner ledgerSigner) {
        this.nodeId = nodeId;
        this.tssLibrary = tssLibrary;
        this.tssParticipantDirectory = tssParticipantDirectory;
        this.ledgerSigner = ledgerSigner;
    }

    /**
     * Submit TSS message transactions to the transaction pool
     */
    public void handleTssMessageTransaction(TssMessageTransactionBody op, HandleContext context) {
        final var targetRosterHash = op.targetRosterHash();
        final var sourceRosterHash = op.sourceRosterHash();
        //1. Add it to the list of TssMessageTransactions for the target roster.
        tssMessages.computeIfAbsent(targetRosterHash, k -> new LinkedList<>()).add(new TssMessage(op.tssMessage().toByteArray()));

        final var tssStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);
        final var hasVoteSubmitted = tssStore.getVote(TssVoteMapKey.newBuilder()
                .nodeId(nodeId.id())
                .rosterHash(targetRosterHash)
                .build()) != null;
        // If the node didn't submit a TssVoteTransaction
        if (!votingClosed.contains(targetRosterHash) && !hasVoteSubmitted) {
            // validate TSS transaction
            final boolean isValid = tssLibrary.verifyTssMessage(tssParticipantDirectory, new TssMessage(op.tssMessage().toByteArray()));
            if (isValid) {
                //2. If the TSS message is valid, set the bit vector for the votes
                tssVoteBitSet.computeIfAbsent(targetRosterHash, k -> new BitSet()).set((int) op.shareIndex());
            }
        }
        nodeShareCounts = sharesFromWeight(rosterStore, sourceRosterHash);
        boolean tssMessageThresholdMet = isThresholdMet(sourceRosterHash, targetRosterHash, context);
        // If public keys for target roster have not been computed yet and the threshold is met, compute the public keys
        if (tssMessageThresholdMet) {
            final var computedPublicShares = tssLibrary.computePublicShares(tssParticipantDirectory, tssMessages.get(targetRosterHash));
            publicShares.put(targetRosterHash, computedPublicShares);
            // compute the ledger id
            final var ledgerId = tssLibrary.aggregatePublicShares(computedPublicShares);
            ledgerIds.put(targetRosterHash, ledgerId);

            // If this node is present in the target roster with same tssEncryptionKey as the source roster, then
            // add the private shares to the list of private shares
            final var sourceRoster = rosterStore.get(sourceRosterHash);
            final var targetRoster = rosterStore.get(targetRosterHash);
            final var recoveredPrivateShares = tssLibrary.decryptPrivateShares(tssParticipantDirectory, tssMessages.get(targetRosterHash));

            final var sourceRosterEncryptionKey = requireNonNull(getRosterEntry(sourceRoster))
                    .get().tssEncryptionKey();
            final var targetRosterEntry = targetRoster.rosterEntries().stream()
                    .filter(e -> e.nodeId() == nodeId.id())
                    .findFirst();
            // If the node is in the roster, but the tssEncryptionKey is different, a restart may be required
            // to pickup the correct encryption key from disk. so, do nothing here
            if (targetRosterEntry.isPresent() && targetRosterEntry.get().tssEncryptionKey().equals(sourceRosterEncryptionKey)) {
                privateShares.put(targetRosterHash, recoveredPrivateShares);
            }

            // If the target roster hash is not in the votingClosed set and there is no TssVoteTransaction
            // from this node
            if(!votingClosed.contains(targetRosterHash)) {
//                ledgerSigner.sign(ledgerId.)
            }
        }

    }

    private Optional<RosterEntry> getRosterEntry(final Roster sourceRoster) {
        return sourceRoster.rosterEntries().stream()
                .filter(e -> e.nodeId() == nodeId.id())
                .findFirst();
    }

    private boolean isThresholdMet(final Bytes sourceRosterHash, Bytes targetRosterHash, final HandleContext context) {
        final boolean thresholdMetForSourceRoster = !createNewLedgerId
                && tssVoteBitSet.get(sourceRosterHash).cardinality() >= tssParticipantDirectory.getThreshold();
        final boolean thresholdMetForTargetRoster = createNewLedgerId && metThresholdWeight(sourceRosterHash, targetRosterHash, context);
        return thresholdMetForSourceRoster || thresholdMetForTargetRoster;
    }

    private boolean metThresholdWeight(Bytes sourceRosterHash, Bytes targetRosterHash, final HandleContext context) {
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);
        final var sourceRoster = rosterStore.get(ProtoBytes.newBuilder().value(sourceRosterHash).build());
        if (sourceRoster == null) {
            return false;
        } else {
            final var numTssMessagesReceived = tssMessages.get(targetRosterHash).size();
            final var numShares = nodeShareCounts.get(sourceRosterHash).values().stream().mapToInt(Integer::intValue).sum();
            // If more than 1/2 the consensus weight has been received, then the threshold is met
            return numTssMessagesReceived >= numShares / 2;
        }
    }

    private Map<Bytes, Map<Long, Integer>> sharesFromWeight(final ReadableRosterStore store, final Bytes rosterHash) {
        final var roster = store.get(ProtoBytes.newBuilder().value(rosterHash).build());
        final var maxWeight = roster.rosterEntries()
                .stream()
                .mapToLong(RosterEntry::weight)
                .max()
                .orElse(0);
        final var shares = new LinkedHashMap<Long, Integer>();
        final var rosterWithShares = new LinkedHashMap<Bytes, Map<Long, Integer>>();
        for (final var entry : roster.rosterEntries()) {
            final var numShares = (int) ((10 * entry.weight() + maxWeight - 1) / maxWeight);
            shares.put(entry.nodeId(), numShares);
        }
        rosterWithShares.put(rosterHash, shares);
        return rosterWithShares;
    }

}
