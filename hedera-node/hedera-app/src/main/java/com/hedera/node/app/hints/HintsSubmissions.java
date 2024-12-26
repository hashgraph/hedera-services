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

package com.hedera.node.app.hints;

import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsAggregationVoteTransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsKeyPublicationTransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsPartialSignatureTransactionBody;
import com.hedera.node.app.hints.impl.HintsKeyAccessor;
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
public class HintsSubmissions {
    private static final Logger log = LogManager.getLogger(HintsSubmissions.class);

    private final Executor executor;
    private final AppContext appContext;
    private final HintsKeyAccessor keyLoader;

    @Inject
    public HintsSubmissions(
            @NonNull final Executor executor,
            @NonNull final AppContext appContext,
            @NonNull final HintsKeyAccessor keyLoader) {
        this.executor = requireNonNull(executor);
        this.keyLoader = requireNonNull(keyLoader);
        this.appContext = requireNonNull(appContext);
    }

    /**
     * Attempts to submit a hinTS key aggregation vote to the network.
     * @param body the vote to submit
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitHintsKey(@NonNull final HintsKeyPublicationTransactionBody body) {
        requireNonNull(body);
        return submit(
                b -> b.hintsKeyPublication(body),
                appContext.configSupplier().get(),
                appContext.selfNodeInfoSupplier().get().accountId(),
                appContext.instantSource().instant());
    }

    /**
     * Attempts to submit a hinTS key aggregation vote to the network.
     * @param body the vote to submit
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitHintsVote(@NonNull final HintsAggregationVoteTransactionBody body) {
        requireNonNull(body);
        return submit(
                b -> b.hintsAggregationVote(body),
                appContext.configSupplier().get(),
                appContext.selfNodeInfoSupplier().get().accountId(),
                appContext.instantSource().instant());
    }

    /**
     * Attempts to submit a hinTS partial signature.
     * @param message the message to sign
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitPartialSignature(@NonNull final Bytes message) {
        requireNonNull(message);
        return submit(
                b -> b.hintsPartialSignature(HintsPartialSignatureTransactionBody.DEFAULT),
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
                        (body, reason) -> log.warn("Failed to submit {} ({})", body, reason));
    }
}
