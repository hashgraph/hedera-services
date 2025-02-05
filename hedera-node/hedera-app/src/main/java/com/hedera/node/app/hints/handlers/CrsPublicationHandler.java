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
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.hints.CRSStage;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.PureChecksContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.swirlds.platform.state.service.ReadableRosterStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CrsPublicationHandler implements TransactionHandler {
    @Inject
    public CrsPublicationHandler() {
        // No-op
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

        final var nextContributionTimeEnd = asTimestamp(context.consensusNow().plusSeconds(10L));
        if (op.hasInitialCrs() && !hintsStore.hasInitialCrs()) {
            final var firstNodeId = activeRosterNodeIds.stream().findFirst().orElse(-1L);
            final var crsState = hintsStore
                    .getCrsState()
                    .copyBuilder()
                    .crs(op.initialCrsOrThrow())
                    .stage(CRSStage.GATHERING_CONTRIBUTIONS)
                    .nextContributingNodeId(firstNodeId)
                    .contributionEndTime(nextContributionTimeEnd)
                    .build();
            hintsStore.setCRSState(crsState);
        } else if (op.hasCrsUpdate()) {
            final var nextNodeId = activeRosterNodeIds.stream()
                    .filter(id -> id > context.networkInfo().selfNodeInfo().nodeId())
                    .findFirst()
                    .orElse(-1L);
            final var crsState = hintsStore
                    .getCrsState()
                    .copyBuilder()
                    .crs(op.crsUpdateOrThrow().newCrs())
                    .contributionEndTime(nextContributionTimeEnd)
                    .stage(CRSStage.GATHERING_CONTRIBUTIONS)
                    .nextContributingNodeId(nextNodeId)
                    .build();
            hintsStore.setCRSState(crsState);
        }
    }
}
