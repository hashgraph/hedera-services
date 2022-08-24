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
package com.hedera.services.state.expiry.renewal;

import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.removal.RemovalWork;
import com.hedera.services.utils.EntityNum;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RenewalProcess {
    private final RenewalRecordsHelper recordsHelper;
    private final RenewalWork renewalWork;
    private final RemovalWork removalWork;
    private final ClassificationWork classifier;
    private Instant cycleTime = null;

    @Inject
    public RenewalProcess(
            final ClassificationWork classifier,
            final RenewalRecordsHelper recordsHelper,
            final RenewalWork renewalWork,
            final RemovalWork removalWork) {
        this.recordsHelper = recordsHelper;
        this.renewalWork = renewalWork;
        this.removalWork = removalWork;
        this.classifier = classifier;
    }

    public void beginRenewalCycle(final Instant currentConsTime) {
        assertNotInCycle();
        cycleTime = currentConsTime;
        recordsHelper.beginRenewalCycle();
    }

    public void endRenewalCycle() {
        assertInCycle();
        cycleTime = null;
        recordsHelper.endRenewalCycle();
    }

    public EntityProcessResult process(final long literalNum) {
        assertInCycle();

        final var longNow = cycleTime.getEpochSecond();
        final var entityNum = EntityNum.fromLong(literalNum);
        // ClassificationWork
        final var classification = classifier.classify(entityNum, longNow);

        return switch (classification) {
            case DETACHED_ACCOUNT_GRACE_PERIOD_OVER -> removalWork.tryToRemoveAccount(entityNum);
            case DETACHED_CONTRACT_GRACE_PERIOD_OVER -> removalWork.tryToRemoveContract(entityNum);
            case EXPIRED_ACCOUNT_READY_TO_RENEW -> renewalWork.tryToRenewAccount(
                    entityNum, cycleTime);
            case EXPIRED_CONTRACT_READY_TO_RENEW -> renewalWork.tryToRenewContract(
                    entityNum, cycleTime);
            default -> NOTHING_TO_DO;
        };
    }

    private void assertInCycle() {
        if (cycleTime == null) {
            throw new IllegalStateException("Cannot stream records if not in a renewal cycle!");
        }
    }

    private void assertNotInCycle() {
        if (cycleTime != null) {
            throw new IllegalStateException("Cannot end renewal cycle, none is started!");
        }
    }

    @VisibleForTesting
    Instant getCycleTime() {
        return cycleTime;
    }
}
