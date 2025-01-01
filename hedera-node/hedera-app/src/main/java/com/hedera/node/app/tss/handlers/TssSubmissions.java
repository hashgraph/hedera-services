/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssShareSignatureTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.TssConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class TssSubmissions {
    private static final Logger log = LogManager.getLogger(TssSubmissions.class);

    /**
     * The executor to use for scheduling the work of submitting transactions.
     */
    private final Executor submissionExecutor;
    /**
     * The next offset to use for the transaction valid start time within the current {@link HandleContext}.
     */
    private final AtomicInteger nextOffset = new AtomicInteger(0);

    private final AppContext appContext;

    /**
     * Tracks which {@link HandleContext} we are currently submitting TSS transactions within, to
     * avoid duplicate transaction ids; even if we do somehow get a duplicate, we still retry up
     * to the configured limit of {@link TssConfig#distinctTxnIdsToTry()}.
     */
    @Nullable
    private HandleContext lastContextUsed;

    @Inject
    public TssSubmissions(@NonNull final AppContext appContext, @NonNull final Executor submissionExecutor) {
        this.appContext = requireNonNull(appContext);
        this.submissionExecutor = requireNonNull(submissionExecutor);
    }

    /**
     * Attempts to submit a TSS message to the network.
     *
     * @param body    the TSS message to submit
     * @param context the TSS context
     * @return a future that completes when the message has been submitted
     */
    public CompletableFuture<Void> submitTssMessage(
            @NonNull final TssMessageTransactionBody body, @NonNull final HandleContext context) {
        return submitTssMessage(body, nextValidStartFor(context));
    }

    /**
     * Attempts to submit a TSS message to the network.
     *
     * @param body    the TSS message to submit
     * @param lastUsedConsensusTime the TSS context
     * @return a future that completes when the message has been submitted
     */
    public CompletableFuture<Void> submitTssMessage(
            @NonNull final TssMessageTransactionBody body, @NonNull final Instant lastUsedConsensusTime) {
        requireNonNull(body);
        requireNonNull(lastUsedConsensusTime);
        return submit(
                b -> b.tssMessage(body),
                appContext.configSupplier().get(),
                appContext.selfNodeInfoSupplier().get().accountId(),
                lastUsedConsensusTime);
    }

    /**
     * Attempts to submit a TSS vote to the network.
     *
     * @param body                  the TSS vote to submit
     * @param context               the TSS context
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitTssVote(
            @NonNull final TssVoteTransactionBody body, final HandleContext context) {
        return submitTssVote(body, nextValidStartFor(context));
    }

    /**
     * Attempts to submit a TSS vote to the network.
     *
     * @param body                  the TSS vote to submit
     * @param lastUsedConsensusTime the
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitTssVote(
            @NonNull final TssVoteTransactionBody body, final Instant lastUsedConsensusTime) {
        requireNonNull(body);
        requireNonNull(lastUsedConsensusTime);
        return submit(
                b -> b.tssVote(body),
                appContext.configSupplier().get(),
                appContext.selfNodeInfoSupplier().get().accountId(),
                lastUsedConsensusTime);
    }

    /**
     * Attempts to submit a TSS share signature to the network.
     *
     * @param body                  the TSS share signature to submit
     * @param lastUsedConsensusTime the last used consensus time
     * @return a future that completes when the share signature has been submitted
     */
    public CompletableFuture<Void> submitTssShareSignature(
            @NonNull final TssShareSignatureTransactionBody body, final Instant lastUsedConsensusTime) {
        requireNonNull(body);
        return submit(
                b -> b.tssShareSignature(body),
                appContext.configSupplier().get(),
                appContext.selfNodeInfoSupplier().get().accountId(),
                lastUsedConsensusTime);
    }

    private CompletableFuture<Void> submit(
            @NonNull final Consumer<TransactionBody.Builder> spec,
            @NonNull final Configuration config,
            @NonNull final AccountID selfId,
            @NonNull final Instant consensusNow) {
        final var tssConfig = config.getConfigData(TssConfig.class);
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        return appContext
                .gossip()
                .submitFuture(
                        selfId,
                        consensusNow,
                        java.time.Duration.of(hederaConfig.transactionMaxValidDuration(), SECONDS),
                        spec,
                        submissionExecutor,
                        tssConfig.timesToTrySubmission(),
                        tssConfig.distinctTxnIdsToTry(),
                        tssConfig.retryDelay(),
                        (body, reason) -> log.warn("Failed to submit {} ({})", body, reason));
    }

    private Instant nextValidStartFor(@NonNull final HandleContext context) {
        if (lastContextUsed != context) {
            lastContextUsed = context;
            nextOffset.set(0);
        } else {
            nextOffset.incrementAndGet();
        }
        return context.consensusNow().plusNanos(nextOffset.get());
    }
}
