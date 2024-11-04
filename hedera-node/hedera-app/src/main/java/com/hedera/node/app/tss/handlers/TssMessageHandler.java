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

package com.hedera.node.app.tss.handlers;

import static com.hedera.node.app.tss.handlers.TssUtils.computeParticipantDirectory;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.tss.TssCryptographyManager;
import com.hedera.node.app.tss.TssMetrics;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.node.config.data.TssConfig;
import com.swirlds.platform.state.service.ReadableRosterStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validates and potentially responds with a vote to a {@link TssMessageTransactionBody}.
 * (TSS-FUTURE) Tracked <a href="https://github.com/hashgraph/hedera-services/issues/14749">here</a>.
 */
@Singleton
public class TssMessageHandler implements TransactionHandler {
    private final TssSubmissions submissionManager;
    private final AppContext.Gossip gossip;
    private final TssCryptographyManager tssCryptographyManager;
    private final TssMetrics tssMetrics;

    @Inject
    public TssMessageHandler(
            @NonNull final TssSubmissions submissionManager,
            @NonNull final AppContext.Gossip gossip,
            @NonNull final TssCryptographyManager tssCryptographyManager,
            @NonNull final TssMetrics metrics) {
        this.submissionManager = requireNonNull(submissionManager);
        this.gossip = requireNonNull(gossip);
        this.tssCryptographyManager = requireNonNull(tssCryptographyManager);
        this.tssMetrics = requireNonNull(metrics);
    }

    @Override
    public void preHandle(@NonNull PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var op = context.body().tssMessageOrThrow();
        final var targetRosterHash = op.targetRosterHash();
        tssMetrics.updateMessagesPerCandidateRoster(targetRosterHash);

        final var tssStore = context.storeFactory().writableStore(WritableTssStore.class);
        final var messageSeqNo = tssStore.getTssMessageBodies(targetRosterHash).size();
        // Nodes vote for a threshold set of TSS messages by their position in consensus order
        final var key = new TssMessageMapKey(targetRosterHash, messageSeqNo);
        // Store the latest message before potentially voting
        tssStore.put(key, op);

        // Obtain the directory of participants for the target roster
        final var maxShares =
                context.configuration().getConfigData(TssConfig.class).maxSharesPerNode();
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);
        final var directory =
                computeParticipantDirectory(requireNonNull(rosterStore.getActiveRoster()), maxShares, (int)
                        context.networkInfo().selfNodeInfo().nodeId());
        // Schedule work to potentially compute a signed vote for the new key material of the target
        // roster, if this message was valid and passed the threshold number of messages required
        tssCryptographyManager
                .getVoteFuture(op.targetRosterHash(), directory, context)
                .thenAccept(vote -> {
                    if (vote != null) {
                        // FUTURE: Validate the ledgerId computed is same as the current ledgerId
                        final var tssVote = TssVoteTransactionBody.newBuilder()
                                .tssVote(vote.bitSet())
                                .sourceRosterHash(op.sourceRosterHash())
                                .targetRosterHash(targetRosterHash)
                                .ledgerId(vote.ledgerId())
                                .nodeSignature(vote.signature().getBytes())
                                .build();
                        submissionManager.submitTssVote(tssVote, context);
                    }
                });
    }
}
