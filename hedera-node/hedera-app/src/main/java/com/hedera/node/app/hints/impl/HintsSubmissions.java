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

package com.hedera.node.app.hints.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsKeyPublicationTransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsPartialSignatureTransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsPreprocessingVoteTransactionBody;
import com.hedera.node.app.hints.HintsKeyAccessor;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.tss.TssSubmissions;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class HintsSubmissions extends TssSubmissions {
    private static final Logger logger = LogManager.getLogger(HintsSubmissions.class);

    private final HintsKeyAccessor keyAccessor;
    private final HintsSigningContext signingContext;
    private final BiConsumer<TransactionBody, String> onFailure =
            (body, reason) -> logger.warn("Failed to submit {} ({})", body, reason);

    @Inject
    public HintsSubmissions(
            @NonNull final Executor executor,
            @NonNull final AppContext appContext,
            @NonNull final HintsKeyAccessor keyAccessor,
            @NonNull final HintsSigningContext signingContext) {
        super(executor, appContext);
        this.keyAccessor = requireNonNull(keyAccessor);
        this.signingContext = requireNonNull(signingContext);
    }

    /**
     * Attempts to submit a hinTS key aggregation vote to the network.
     * @param body the vote to submit
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitHintsKey(@NonNull final HintsKeyPublicationTransactionBody body) {
        requireNonNull(body);
        return submit(b -> b.hintsKeyPublication(body), onFailure);
    }

    /**
     * Attempts to submit a hinTS key aggregation vote to the network.
     * @param body the vote to submit
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitHintsVote(@NonNull final HintsPreprocessingVoteTransactionBody body) {
        requireNonNull(body);
        return submit(b -> b.hintsAggregationVote(body), onFailure);
    }

    /**
     * Attempts to submit a hinTS partial signature.
     * @param message the message to sign
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitPartialSignature(@NonNull final Bytes message) {
        requireNonNull(message);
        final long constructionId = signingContext.activeConstructionIdOrThrow();
        return submit(
                b -> {
                    final var signature = keyAccessor.signWithBlsPrivateKey(constructionId, message);
                    b.hintsPartialSignature(
                            new HintsPartialSignatureTransactionBody(constructionId, message, signature));
                },
                onFailure);
    }
}
