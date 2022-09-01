/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.expiry;

import static com.hedera.services.state.expiry.EntityProcessResult.DONE;
import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.throttling.ExpiryThrottle;
import java.time.Instant;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class EntityAutoExpiry {
    private static final Logger log = LogManager.getLogger(EntityAutoExpiry.class);

    private final long firstEntityToScan;
    private final ExpiryThrottle expiryThrottle;
    private final AutoExpiryCycle autoExpiryCycle;
    private final NetworkCtxManager networkCtxManager;
    private final GlobalDynamicProperties dynamicProps;
    private final Supplier<MerkleNetworkContext> networkCtx;
    private final Supplier<SequenceNumber> seqNo;
    private final ConsensusTimeTracker consensusTimeTracker;

    @Inject
    public EntityAutoExpiry(
            final HederaNumbers hederaNumbers,
            final ExpiryThrottle expiryThrottle,
            final AutoExpiryCycle autoExpiryCycle,
            final GlobalDynamicProperties dynamicProps,
            final NetworkCtxManager networkCtxManager,
            final Supplier<MerkleNetworkContext> networkCtx,
            final ConsensusTimeTracker consensusTimeTracker,
            final Supplier<SequenceNumber> seqNo) {
        this.seqNo = seqNo;
        this.networkCtx = networkCtx;
        this.networkCtxManager = networkCtxManager;
        this.expiryThrottle = expiryThrottle;
        this.autoExpiryCycle = autoExpiryCycle;
        this.dynamicProps = dynamicProps;
        this.consensusTimeTracker = consensusTimeTracker;

        this.firstEntityToScan = hederaNumbers.numReservedSystemEntities() + 1;
    }

    public void execute(final Instant currentConsTime) {
        executeInternal(currentConsTime);
        // Ensure we capture any capacity used in the expiry throttle
        networkCtx.get().syncExpiryThrottle(expiryThrottle);
    }

    private void executeInternal(final Instant currentConsTime) {
        if (!dynamicProps.shouldAutoRenewSomeEntityType()) {
            return;
        }

        final long wrapNum = seqNo.get().current();
        if (wrapNum == firstEntityToScan) {
            /* No non-system entities in the system, can abort */
            return;
        }

        if (!consensusTimeTracker.hasMoreStandaloneRecordTime()) {
            log.debug(
                    "Auto-renew scan skipped because there are no more standalone record times. {}",
                    consensusTimeTracker);
            return;
        }

        final var curNetworkCtx = networkCtx.get();
        final int maxEntitiesToTouch = dynamicProps.autoRenewMaxNumberOfEntitiesToRenewOrDelete();
        final int maxEntitiesToScan = dynamicProps.autoRenewNumberOfEntitiesToScan();
        if (networkCtxManager.currentTxnIsFirstInConsensusSecond()) {
            curNetworkCtx.clearAutoRenewSummaryCounts();
        }
        autoExpiryCycle.beginCycle(currentConsTime);

        int i = 1;
        int entitiesTouched = 0;
        long scanNum = curNetworkCtx.lastScannedEntity();
        boolean advanceScan = true;
        EntityProcessResult result;
        log.debug(
                "Auto-renew scan beginning from last DONE @ {}, wrapping at {}", scanNum, wrapNum);
        for (; i <= maxEntitiesToScan; i++) {
            if (advanceScan) {
                scanNum = next(scanNum, wrapNum);
            }
            if ((result = autoExpiryCycle.process(scanNum)) != NOTHING_TO_DO) {
                entitiesTouched++;
                advanceScan = (result == DONE);
            } else {
                advanceScan = true;
            }
            if ((entitiesTouched >= maxEntitiesToTouch)
                    || (!consensusTimeTracker.hasMoreStandaloneRecordTime())) {
                // Allow consistent calculation of num scanned below.
                i++;
                break;
            }
        }

        autoExpiryCycle.endCycle();
        curNetworkCtx.updateAutoRenewSummaryCounts(i - 1, entitiesTouched);
        curNetworkCtx.updateLastScannedEntity(advanceScan ? scanNum : scanNum - 1);
        log.debug(
                "Auto-renew scan finished at {} with {}/{} scanned/touched (Total this second:"
                        + " {}/{})",
                scanNum,
                i - 1,
                entitiesTouched,
                curNetworkCtx.getEntitiesScannedThisSecond(),
                curNetworkCtx.getEntitiesTouchedThisSecond());
    }

    private long next(long scanNum, final long wrapNum) {
        return (++scanNum >= wrapNum) ? firstEntityToScan : scanNum;
    }
}
