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

import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.hapi.node.state.hints.PreprocessingVote;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.hints.CRSUpdate;
import com.hedera.hapi.services.auxiliary.hints.CrsPublicationTransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsKeyPublicationTransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsPartialSignatureTransactionBody;
import com.hedera.hapi.services.auxiliary.hints.HintsPreprocessingVoteTransactionBody;
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

    private final HintsContext context;
    private final HintsKeyAccessor keyAccessor;
    private final BiConsumer<TransactionBody, String> onFailure =
            (body, reason) -> logger.warn("Failed to submit {} ({})", body, reason);

    @Inject
    public HintsSubmissions(
            @NonNull final Executor executor,
            @NonNull final AppContext appContext,
            @NonNull final HintsKeyAccessor keyAccessor,
            @NonNull final HintsContext context) {
        super(executor, appContext);
        this.keyAccessor = requireNonNull(keyAccessor);
        this.context = requireNonNull(context);
    }

    /**
     * Attempts to submit a hinTS key aggregation vote to the network.
     * @param partyId the ID of the party submitting the vote
     * @param numParties the total number of parties in the vote
     * @param hintsKey the key to vote for
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitHintsKey(
            final int partyId, final int numParties, @NonNull final Bytes hintsKey) {
        requireNonNull(hintsKey);
        final var op = new HintsKeyPublicationTransactionBody(partyId, numParties, hintsKey);
        return submit(b -> b.hintsKeyPublication(op), onFailure);
    }

    /**
     * Attempts to submit a CRS update to the network.
     * @param crs the updated CRS
     * @return a future that completes when the update has been submitted
     */
    public CompletableFuture<Void> submitUpdateCRS(@NonNull final CRSUpdate crs) {
        requireNonNull(crs);
        final var op = CrsPublicationTransactionBody.newBuilder().crsUpdate(crs).build();
        return submit(b -> b.crsPublication(op), onFailure);
    }

    /**
     * Submits a vote for the same hinTS preprocessing output for the given construction id that another
     * node with the given ID has already voted for.
     * @param constructionId the construction ID to vote for
     * @param congruentNodeId the ID of the node that has already voted
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitHintsVote(final long constructionId, final long congruentNodeId) {
        final var op = HintsPreprocessingVoteTransactionBody.newBuilder()
                .constructionId(constructionId)
                .vote(PreprocessingVote.newBuilder()
                        .congruentNodeId(congruentNodeId)
                        .build())
                .build();
        return submit(b -> b.hintsPreprocessingVote(op), onFailure);
    }

    /**
     * Submits a vote for the given hinTS preprocessing output for the given construction id.
     * @param constructionId the construction ID to vote for
     * @param preprocessedKeys the keys to vote for
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitHintsVote(
            final long constructionId, @NonNull final PreprocessedKeys preprocessedKeys) {
        final var op = HintsPreprocessingVoteTransactionBody.newBuilder()
                .constructionId(constructionId)
                .vote(PreprocessingVote.newBuilder()
                        .preprocessedKeys(preprocessedKeys)
                        .build())
                .build();
        return submit(b -> b.hintsPreprocessingVote(op), onFailure);
    }

    /**
     * Attempts to submit a hinTS partial signature.
     * @param message the message to sign
     * @return a future that completes when the vote has been submitted
     */
    public CompletableFuture<Void> submitPartialSignature(@NonNull final Bytes message) {
        requireNonNull(message);
        final long constructionId = context.constructionIdOrThrow();
        return submit(
                b -> {
                    final var signature = keyAccessor.signWithBlsPrivateKey(constructionId, message);
                    b.hintsPartialSignature(
                            new HintsPartialSignatureTransactionBody(constructionId, message, signature));
                },
                onFailure);
    }
}
