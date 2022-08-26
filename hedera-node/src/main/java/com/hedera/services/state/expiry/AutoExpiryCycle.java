/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;
import static com.hedera.services.state.expiry.EntityProcessResult.STILL_MORE_TO_DO;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.removal.RemovalWork;
import com.hedera.services.state.expiry.renewal.RenewalWork;
import com.hedera.services.utils.EntityNum;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class AutoExpiryCycle {
    private static final Logger log = LogManager.getLogger(AutoExpiryCycle.class);
    private final RenewalWork renewalWork;
    private final RemovalWork removalWork;
    private final ClassificationWork classifier;
    private Instant cycleTime = null;

    @Inject
    public AutoExpiryCycle(
            final ClassificationWork classifier,
            final RenewalWork renewalWork,
            final RemovalWork removalWork) {
        this.renewalWork = renewalWork;
        this.removalWork = removalWork;
        this.classifier = classifier;
    }

    public void beginCycle(final Instant currentConsTime) {
        warnIfInCycle();
        cycleTime = currentConsTime;
    }

    public EntityProcessResult process(final long literalNum) {
        warnIfNotInCycle();

        final var entityNum = EntityNum.fromLong(literalNum);
        final var result = classifier.classify(entityNum, cycleTime);
        return switch (result) {
            case COME_BACK_LATER -> STILL_MORE_TO_DO;

            case EXPIRED_ACCOUNT_READY_TO_RENEW -> renewalWork.tryToRenewAccount(
                    entityNum, cycleTime);
            case DETACHED_ACCOUNT_GRACE_PERIOD_OVER -> removalWork.tryToRemoveAccount(entityNum);

            case EXPIRED_CONTRACT_READY_TO_RENEW -> renewalWork.tryToRenewContract(
                    entityNum, cycleTime);
            case DETACHED_CONTRACT_GRACE_PERIOD_OVER -> removalWork.tryToRemoveContract(entityNum);

            default -> NOTHING_TO_DO;
        };
    }

    public void endCycle() {
        warnIfNotInCycle();
        cycleTime = null;
    }

    private void warnIfNotInCycle() {
        if (cycleTime == null) {
            log.warn("Cycle ended, but did not have a start time");
        }
    }

    private void warnIfInCycle() {
        if (cycleTime != null) {
            log.warn("Cycle started, but had not ended from {}", cycleTime);
        }
    }

    @VisibleForTesting
    Instant getCycleTime() {
        return cycleTime;
    }
}
