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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.tss.TssMetrics;
import com.hedera.node.app.tss.stores.WritableTssStore;
import com.swirlds.platform.state.service.ReadableRosterStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.InstantSource;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validates and responds to a {@link TssVoteTransactionBody}.
 * <p>Tracked <a href="https://github.com/hashgraph/hedera-services/issues/14750">here</a>
 */
@Singleton
public class TssVoteHandler implements TransactionHandler {
    private final TssMetrics tssMetrics;
    private final InstantSource instantSource;

    @Inject
    public TssVoteHandler(@NonNull final TssMetrics tssMetrics, @NonNull final InstantSource instantSource) {
        this.tssMetrics = requireNonNull(tssMetrics);
        this.instantSource = instantSource;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var op = context.body().tssVoteOrThrow();
        final var tssStore = context.storeFactory().writableStore(WritableTssStore.class);
        final var targetRosterHash = op.targetRosterHash();
        tssMetrics.updateVotesPerCandidateRoster(targetRosterHash);
        final var key =
                new TssVoteMapKey(targetRosterHash, context.creatorInfo().nodeId());
        if (tssStore.exists(key)) {
            // Ignore duplicate votes
            return;
        }
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);
        final var winningVote = tssStore.anyWinningVoteFrom(op.sourceRosterHash(), targetRosterHash, rosterStore);
        if (winningVote.isEmpty()) {
            // The election for target roster keys is not yet resolved, so include this vote
            tssStore.put(key, op);
            // And check if this vote was the winning vote
            final var newWinningVote =
                    tssStore.anyWinningVoteFrom(op.sourceRosterHash(), targetRosterHash, rosterStore);
            if (newWinningVote.isPresent()) {
                // Update metrics for how long it took to receive a winning vote
                tssMetrics.updateCandidateRosterLifecycle(instantSource.instant());
            }
        }
    }
}
