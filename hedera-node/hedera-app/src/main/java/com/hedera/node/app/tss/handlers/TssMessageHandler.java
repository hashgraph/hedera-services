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

import static com.hedera.node.app.tss.handlers.TssUtils.computeTssParticipantDirectory;
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
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.Metrics;
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

    private static final String TSS_MESSAGE_COUNTER_METRIC = "tss_message_total";
    private static final String TSS_MESSAGE_COUNTER_METRIC_DESC = "total numbers of tss message transactions";
    private static final Counter.Config TSS_MESSAGE_TX_COUNTER =
            new Counter.Config("app", TSS_MESSAGE_COUNTER_METRIC).withDescription(TSS_MESSAGE_COUNTER_METRIC_DESC);
    private final Counter tssMessageTxCounter;

    @Inject
    public TssMessageHandler(
            @NonNull final TssSubmissions submissionManager,
            @NonNull final AppContext.Gossip gossip,
            @NonNull final TssCryptographyManager tssCryptographyManager,
            @NonNull final Metrics metrics) {
        this.submissionManager = requireNonNull(submissionManager);
        this.gossip = requireNonNull(gossip);
        this.tssCryptographyManager = requireNonNull(tssCryptographyManager);
        requireNonNull(metrics);
        tssMessageTxCounter = metrics.getOrCreate(TSS_MESSAGE_TX_COUNTER);
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

        final var tssStore = context.storeFactory().writableStore(WritableTssStore.class);
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);
        final var maxSharesPerNode =
                context.configuration().getConfigData(TssConfig.class).maxSharesPerNode();
        final var numberOfAlreadyExistingMessages =
                tssStore.getTssMessages(op.targetRosterHash()).size();

        // The sequence number starts from 0 and increments by 1 for each new message.
        final var key = TssMessageMapKey.newBuilder()
                .rosterHash(op.targetRosterHash())
                .sequenceNumber(numberOfAlreadyExistingMessages)
                .build();
        // Each tss message is stored in the tss message state and is sent to CryptographyManager for further
        // processing.
        tssStore.put(key, op);

        final var tssParticipantDirectory =
                computeTssParticipantDirectory(rosterStore.getActiveRoster(), maxSharesPerNode, (int)
                        context.networkInfo().selfNodeInfo().nodeId());
        final var result = tssCryptographyManager.handleTssMessageTransaction(op, tssParticipantDirectory, context);
        // tss aggregation end
        result.thenAccept(ledgerIdAndSignature -> {
            if (ledgerIdAndSignature != null) {
                final var signature =
                        gossip.sign(ledgerIdAndSignature.ledgerId().publicKey().toBytes());
                // FUTURE: Validate the ledgerId computed is same as the current ledgerId
                final var tssVote = TssVoteTransactionBody.newBuilder()
                        .tssVote(Bytes.wrap(ledgerIdAndSignature.tssVoteBitSet().toByteArray()))
                        .targetRosterHash(op.targetRosterHash())
                        .sourceRosterHash(op.sourceRosterHash())
                        .nodeSignature(signature.getBytes())
                        .ledgerId(Bytes.wrap(
                                ledgerIdAndSignature.ledgerId().publicKey().toBytes()))
                        .build();
                submissionManager.submitTssVote(tssVote, context);
            }
        });
    }
}
