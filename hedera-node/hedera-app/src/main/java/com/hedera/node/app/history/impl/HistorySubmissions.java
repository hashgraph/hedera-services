/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history.impl;

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.history.HistoryAssemblySignature;
import com.hedera.hapi.node.state.history.MetadataProof;
import com.hedera.hapi.node.state.history.MetadataProofVote;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.history.HistoryAssemblySignatureTransactionBody;
import com.hedera.hapi.services.auxiliary.history.HistoryProofKeyPublicationTransactionBody;
import com.hedera.hapi.services.auxiliary.history.HistoryProofVoteTransactionBody;
import com.hedera.node.app.history.ProofKeysAccessor;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class HistorySubmissions {
    private static final Logger logger = LogManager.getLogger(HistorySubmissions.class);

    private final Executor executor;
    private final AppContext appContext;
    private final ProofKeysAccessor keyAccessor;

    @Inject
    public HistorySubmissions(
            @NonNull final Executor executor,
            @NonNull final AppContext appContext,
            @NonNull final ProofKeysAccessor keyAccessor) {
        this.executor = requireNonNull(executor);
        this.appContext = requireNonNull(appContext);
        this.keyAccessor = requireNonNull(keyAccessor);
    }

    /**
     * Submits a proof key publication to the network.
     * @param proofKey the proof key to publish
     * @return a future that completes with the submission
     */
    public CompletableFuture<Void> submitProofKeyPublication(@NonNull final Bytes proofKey) {
        requireNonNull(proofKey);
        return submit(
                b -> b.historyProofKeyPublication(new HistoryProofKeyPublicationTransactionBody(proofKey)),
                appContext.configSupplier().get(),
                appContext.selfNodeInfoSupplier().get().accountId(),
                appContext.instantSource().instant());
    }

    /**
     * Submits a proof vote to the network.
     * @param constructionId the construction id to vote on
     * @param metadataProof the metadata proof to vote for
     * @return a future that completes with the submission
     */
    public CompletableFuture<Void> submitProofVote(
            final long constructionId, @NonNull final MetadataProof metadataProof) {
        requireNonNull(metadataProof);
        final var vote =
                MetadataProofVote.newBuilder().metadataProof(metadataProof).build();
        return submit(
                b -> b.historyProofVote(new HistoryProofVoteTransactionBody(constructionId, vote)),
                appContext.configSupplier().get(),
                appContext.selfNodeInfoSupplier().get().accountId(),
                appContext.instantSource().instant());
    }

    /**
     * Submits an assembly signature to the network.
     * @return a future that completes with the submission
     */
    public CompletableFuture<Void> submitAssemblySignature(
            final long constructionId, @NonNull final HistoryAssemblySignature signature) {
        requireNonNull(signature);
        return submit(
                b -> b.historyAssemblySignature(new HistoryAssemblySignatureTransactionBody(constructionId, signature)),
                appContext.configSupplier().get(),
                appContext.selfNodeInfoSupplier().get().accountId(),
                appContext.instantSource().instant());
    }

    private CompletableFuture<Void> submit(
            @NonNull final Consumer<TransactionBody.Builder> spec,
            @NonNull final Configuration config,
            @NonNull final AccountID selfId,
            @NonNull final Instant consensusNow) {
        final var adminConfig = config.getConfigData(NetworkAdminConfig.class);
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        return appContext
                .gossip()
                .submitFuture(
                        selfId,
                        consensusNow,
                        java.time.Duration.of(hederaConfig.transactionMaxValidDuration(), SECONDS),
                        spec,
                        executor,
                        adminConfig.timesToTrySubmission(),
                        adminConfig.distinctTxnIdsToTry(),
                        adminConfig.retryDelay(),
                        (body, reason) -> logger.warn("Failed to submit {} ({})", body, reason));
    }
}
