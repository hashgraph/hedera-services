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

package com.hedera.node.app.hints.handlers;

import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.app.hints.impl.HintsControllerImpl.CONTRIBUTION_DURATION_PER_NODE_SECS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.services.auxiliary.hints.CrsPublicationTransactionBody;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PureChecksContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.swirlds.platform.state.service.ReadableRosterStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CrsPublicationHandler implements TransactionHandler {
    private final HintsLibrary library;

    @Inject
    public CrsPublicationHandler(final HintsLibrary library) {
        this.library = requireNonNull(library);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
    }

    @Override
    public void pureChecks(@NonNull final PureChecksContext context) throws PreCheckException {
        requireNonNull(context);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var op = context.body().crsPublicationOrThrow();
        final var hintsStore = context.storeFactory().writableStore(WritableHintsStore.class);
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);
        final var activeRosterNodeIds = requireNonNull(rosterStore.getActiveRoster()).rosterEntries().stream()
                .map(RosterEntry::nodeId)
                .sorted()
                .toList();

        final var nextContributionTimeEnd =
                asTimestamp(context.consensusNow().plusSeconds(CONTRIBUTION_DURATION_PER_NODE_SECS));
        final var selfNodeId = context.networkInfo().selfNodeInfo().nodeId();
        if (isFirstInitialCRS(op, hintsStore)) {
            putInitialCrs(activeRosterNodeIds, hintsStore, op, nextContributionTimeEnd);
        } else if (op.hasCrsUpdate()) {
            updateCrs(selfNodeId, activeRosterNodeIds, hintsStore, op, nextContributionTimeEnd);
        }
    }

    private void updateCrs(
            final long selfNodeId,
            @NonNull final List<Long> activeRosterNodeIds,
            @NonNull final WritableHintsStore hintsStore,
            @NonNull final CrsPublicationTransactionBody op,
            @NonNull final Timestamp nextContributionTimeEnd) {
        final var nextNodeId = activeRosterNodeIds.stream()
                .filter(id -> id > selfNodeId)
                .findFirst()
                .orElse(-1L);
        final var oldCrs = hintsStore.getCrsState().crs();
        final var isValid = library.verifyCrsUpdate(
                oldCrs, op.crsUpdateOrThrow().newCrs(), op.crsUpdateOrThrow().proof());
        if (isValid) {
            hintsStore.updateCrs(op.crsUpdateOrThrow().newCrs(), nextNodeId, nextContributionTimeEnd);
        }
    }

    private void putInitialCrs(
            @NonNull final List<Long> activeRosterNodeIds,
            @NonNull final WritableHintsStore hintsStore,
            @NonNull final CrsPublicationTransactionBody op,
            @NonNull final Timestamp nextContributionTimeEnd) {
        final var firstNodeId = activeRosterNodeIds.stream().findFirst().orElse(-1L);
        if (op.initialCrsOrThrow().length() > 0) {
            hintsStore.putInitialCrs(op.initialCrsOrThrow(), firstNodeId, nextContributionTimeEnd);
        }
    }

    private boolean isFirstInitialCRS(
            @NonNull final CrsPublicationTransactionBody op, @NonNull final WritableHintsStore hintsStore) {
        return op.hasInitialCrs() && !hintsStore.hasInitialCrs();
    }
}
