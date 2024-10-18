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
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.node.app.roster.ReadableRosterStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is yet to be implemented
 */
@Singleton
public class TssCryptographyManager {
    private static final int NUM_MAX_SHARES_PER_NODE = 10;
    private Map<Bytes, List<TssMessageTransactionBody>> tssMessages = new ConcurrentHashMap<>();
    Map<Bytes, List<TssVoteTransactionBody>> tssVotes = new ConcurrentHashMap<>();
    Map<Bytes, BitSet> tssVoteBitSet = new ConcurrentHashMap<>();
    private Set<Bytes> votingClosed = new LinkedHashSet();
    private boolean createNewLedgerId = false;
    private NodeId nodeId;

    private final TssLibrary tssLibrary;
    private TssParticipantDirectory tssParticipantDirectory;

    public TssCryptographyManager(NodeId nodeId,
                                  TssLibrary tssLibrary,
                                  TssParticipantDirectory tssParticipantDirectory) {
        this.nodeId = nodeId;
        this.tssLibrary = tssLibrary;
        this.tssParticipantDirectory = tssParticipantDirectory;
        // This is yet to be implemented
    }

    /**
     * Submit TSS message transactions to the transaction pool
     */
    public void handleTssMessageTransaction(TssMessageTransactionBody op, HandleContext context) {
        final var targetRoster = op.targetRosterHash();
        final var sourceRoster = op.sourceRosterHash();
        //1. Add it to the list of TssMessageTransactions for the target roster.
        tssMessages.computeIfAbsent(targetRoster, k -> new LinkedList<>()).add(op);

        final var tssStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var hasVoteSubmitted = tssStore.getVote(TssVoteMapKey.newBuilder()
                .nodeId(nodeId.id())
                .rosterHash(targetRoster)
                .build()) != null;
        // If the node didn't submit a TssVoteTransaction
        if (!votingClosed.contains(targetRoster) && !hasVoteSubmitted) {
            // validate TSS transaction
            final boolean isValid = tssLibrary.verifyTssMessage(tssParticipantDirectory, new TssMessage(op.tssMessage().toByteArray()));
            if (isValid) {
                //2. If the TSS message is valid, set the bit vector for the votes
                tssVoteBitSet.computeIfAbsent(targetRoster, k -> new BitSet()).set((int) op.shareIndex());
            }

        }
        final boolean tssMessageThresholdMet = false;

        if(isThresholdMet(sourceRoster, context)) {
            //3. If the threshold is met, create a new ledger id
            createNewLedgerId = true;
        }

    }

    private boolean isThresholdMet(final Bytes sourceRosterHash, final HandleContext context) {
       final boolean thresholdMetForSourceRoster = !createNewLedgerId
               && tssVoteBitSet.get(sourceRosterHash).cardinality() >= tssParticipantDirectory.getThreshold();
       final boolean thresholdMetForTargetRoster = createNewLedgerId && metThresholdWeight(sourceRosterHash, context);
       return thresholdMetForSourceRoster || thresholdMetForTargetRoster;
    }

    private boolean metThresholdWeight(Bytes sourceRosterHash, final HandleContext context) {
        final var tssStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);
        final var sourceRoster = rosterStore.get(ProtoBytes.newBuilder().value(sourceRosterHash).build());
        if(sourceRoster == null) {
            return false;
        } else {
            final var totalWeight = sourceRoster.rosterEntries().stream().mapToLong(RosterEntry::weight).sum();

        }
    }

    private Map<Long, Long> sharesFromWeight(final ReadableRosterStore store, final Bytes rosterHash){
        final var roster = store.get(ProtoBytes.newBuilder().value(rosterHash).build());
        final var maxWeight = roster.rosterEntries()
                .stream()
                .mapToLong(RosterEntry::weight)
                .max()
                .orElse(0);
        final var shares = new LinkedHashMap<Long, Long>();
        for(final var entry : roster.rosterEntries()){
            final var numShares = (10 * entry.weight() + maxWeight - 1)/ maxWeight;
            shares.put(entry.nodeId(), numShares);
        }
        return shares;
    }

}
