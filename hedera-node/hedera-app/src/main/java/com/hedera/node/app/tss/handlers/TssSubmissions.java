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

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.TssConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class TssSubmissions {
    private static final Logger log = LogManager.getLogger(TssSubmissions.class);

    private static final String DUPLICATE_TRANSACTION_REASON = "" + DUPLICATE_TRANSACTION;

    private final AppContext.Gossip gossip;
    private final Executor submissionExecutor;

    @Inject
    public TssSubmissions(@NonNull final AppContext.Gossip gossip, @NonNull final Executor submissionExecutor) {
        this.gossip = requireNonNull(gossip);
        this.submissionExecutor = requireNonNull(submissionExecutor);
    }

    /**
     * Attempts to submit a TSS message to the network.
     *
     * @param body the TSS message to submit
     * @param context the TSS context
     * @return a future that completes when the message has been submitted
     */
    public CompletableFuture<Void> submitTssMessage(
            @NonNull final TssMessageTransactionBody body, @NonNull final HandleContext context) {
        requireNonNull(body);
        requireNonNull(context);
        return submit(b -> b.tssMessage(body), context);
    }

    /**
     * Attempts to submit a TSS vote to the network.
     *
     * @param body the TSS vote to submit
     * @param context the TSS context
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitTssVote(
            @NonNull final TssVoteTransactionBody body, @NonNull final HandleContext context) {
        requireNonNull(body);
        requireNonNull(context);
        return submit(b -> b.tssVote(body), context);
    }

    private CompletableFuture<Void> submit(
            @NonNull final Consumer<TransactionBody.Builder> spec, @NonNull final HandleContext context) {
        final var config = context.configuration();
        final var tssConfig = config.getConfigData(TssConfig.class);
        final var hederaConfig = config.getConfigData(HederaConfig.class);
        final var validDuration = new Duration(hederaConfig.transactionMaxValidDuration());
        final var validStartTime = new AtomicReference<>(context.consensusNow());
        final var attemptsLeft = new AtomicInteger(tssConfig.timesToTrySubmission());
        return CompletableFuture.runAsync(
                () -> {
                    System.out.println("Starting async block execution");
                    var fatalFailure = false;
                    var failureReason = "<N/A>";
                    TransactionBody body;
                    do {
                        int txnIdsLeft = tssConfig.distinctTxnIdsToTry();
                        do {
                            final var builder = builderWith(
                                    validStartTime.get(),
                                    context.networkInfo().selfNodeInfo().accountId(),
                                    validDuration);
                            spec.accept(builder);
                            body = builder.build();
                            try {
                                gossip.submit(body);
                                return;
                            } catch (IllegalArgumentException iae) {
                                failureReason = iae.getMessage();
                                if (DUPLICATE_TRANSACTION_REASON.equals(failureReason)) {
                                    validStartTime.set(validStartTime.get().plusNanos(1));
                                } else {
                                    fatalFailure = true;
                                    break;
                                }
                            } catch (IllegalStateException ise) {
                                failureReason = ise.getMessage();
                                // There is no point to retry immediately except on a duplicate id
                                break;
                            }
                        } while (txnIdsLeft-- > 1);
                        log.warn("Failed to submit {} ({})", body, failureReason);
                        try {
                            MILLISECONDS.sleep(tssConfig.retryDelay().toMillis());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("Interrupted while waiting to retry " + body, e);
                        }
                    } while (!fatalFailure && attemptsLeft.decrementAndGet() > 0);
                    throw new IllegalStateException(failureReason);
                },
                submissionExecutor);
    }

    private TransactionBody.Builder builderWith(
            @NonNull final Instant validStartTime,
            @NonNull final AccountID selfAccountId,
            @NonNull final Duration validDuration) {
        return TransactionBody.newBuilder()
                .nodeAccountID(selfAccountId)
                .transactionValidDuration(validDuration)
                .transactionID(new TransactionID(asTimestamp(validStartTime), selfAccountId, false, 0));
    }
}
