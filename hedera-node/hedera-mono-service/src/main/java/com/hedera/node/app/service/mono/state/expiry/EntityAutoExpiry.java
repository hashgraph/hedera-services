/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.expiry;

import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.DONE;
import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.NEEDS_DIFFERENT_CONTEXT;
import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.NOTHING_TO_DO;
import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.NO_CAPACITY_LEFT;

import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.records.ConsensusTimeTracker;
import com.hedera.node.app.service.mono.records.RecordsHistorian;
import com.hedera.node.app.service.mono.state.logic.NetworkCtxManager;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.submerkle.SequenceNumber;
import com.hedera.node.app.service.mono.state.tasks.SystemTaskManager;
import com.hedera.node.app.service.mono.state.tasks.SystemTaskResult;
import com.hedera.node.app.service.mono.stats.ExpiryStats;
import com.hedera.node.app.service.mono.throttling.ExpiryThrottle;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EntityAutoExpiry {
    private final long firstEntityToScan;
    private final ExpiryThrottle expiryThrottle;
    private final SystemTaskManager taskManager;
    private final RecordsHistorian recordsHistorian;
    private final NetworkCtxManager networkCtxManager;
    private final GlobalDynamicProperties dynamicProps;
    private final Supplier<MerkleNetworkContext> networkCtx;
    private final Supplier<SequenceNumber> seqNo;
    private final ConsensusTimeTracker consensusTimeTracker;
    private final ExpiryStats expiryStats;

    private int maxIdsToScan;
    private int maxEntitiesToProcess;

    @Inject
    public EntityAutoExpiry(
            final ExpiryStats expiryStats,
            final HederaNumbers hederaNumbers,
            final ExpiryThrottle expiryThrottle,
            final RecordsHistorian recordsHistorian,
            final SystemTaskManager taskManager,
            final GlobalDynamicProperties dynamicProps,
            final NetworkCtxManager networkCtxManager,
            final Supplier<MerkleNetworkContext> networkCtx,
            final ConsensusTimeTracker consensusTimeTracker,
            final Supplier<SequenceNumber> seqNo) {
        this.seqNo = seqNo;
        this.expiryStats = expiryStats;
        this.networkCtx = networkCtx;
        this.recordsHistorian = recordsHistorian;
        this.networkCtxManager = networkCtxManager;
        this.expiryThrottle = expiryThrottle;
        this.dynamicProps = dynamicProps;
        this.taskManager = taskManager;
        this.consensusTimeTracker = consensusTimeTracker;

        this.firstEntityToScan = hederaNumbers.numReservedSystemEntities() + 1;
    }

    public void execute(final Instant currentConsTime) {
        executeInternal(currentConsTime);
        // Ensure we capture any capacity used in the expiry throttle
        networkCtx.get().syncExpiryThrottle(expiryThrottle);
    }

    private void executeInternal(final Instant now) {
        final long wrapNum = seqNo.get().current();
        // If all other conditions are met, has a side effect of draining
        // the expiry throttle bucket from all the time elapsed until now
        if (!canDoWorkGiven(wrapNum, now)) {
            return;
        }

        final var curNetworkCtx = networkCtx.get();
        maxIdsToScan = dynamicProps.autoRenewNumberOfEntitiesToScan();
        maxEntitiesToProcess = dynamicProps.autoRenewMaxNumberOfEntitiesToRenewOrDelete();
        if (networkCtxManager.currentTxnIsFirstInConsensusSecond()) {
            expiryStats.includeIdsScannedInLastConsSec(curNetworkCtx.idsScannedThisSecond());
            curNetworkCtx.clearAutoRenewSummaryCounts();
        }

        int idsScanned = 0;
        int entitiesProcessed = 0;
        long scanNum = curNetworkCtx.lastScannedEntity();
        boolean advanceScan = true;
        SystemTaskResult result = null;
        while (canContinueGiven(result, idsScanned, entitiesProcessed)) {
            if (advanceScan) {
                scanNum = next(scanNum, wrapNum);
                idsScanned++;
            }
            result = taskManager.process(scanNum, now, curNetworkCtx);
            if (result == NOTHING_TO_DO) {
                advanceScan = true;
            } else {
                advanceScan = (result == DONE);
                if (advanceScan) {
                    entitiesProcessed++;
                }
            }
        }

        curNetworkCtx.updateAutoRenewSummaryCounts(idsScanned, entitiesProcessed);
        curNetworkCtx.updateLastScannedEntity(advanceScan ? scanNum : scanNum - 1);
    }

    private boolean canContinueGiven(
            final @Nullable SystemTaskResult result,
            final int idsScanned,
            final int entitiesProcessed) {
        return idsScanned < maxIdsToScan
                && entitiesProcessed < maxEntitiesToProcess
                && result != NO_CAPACITY_LEFT
                && result != NEEDS_DIFFERENT_CONTEXT
                && consensusTimeTracker.hasMoreStandaloneRecordTime();
    }

    private boolean canDoWorkGiven(final long wrapNum, final Instant now) {
        return wrapNum != firstEntityToScan
                && dynamicProps.shouldAutoRenewSomeEntityType()
                && consensusTimeTracker.hasMoreStandaloneRecordTime()
                && !recordsHistorian.nextSystemTransactionIdIsUnknown()
                && !expiryThrottle.stillLacksMinFreeCapAfterLeakingUntil(now);
    }

    private long next(long scanNum, final long wrapNum) {
        return (++scanNum >= wrapNum) ? firstEntityToScan : scanNum;
    }
}
