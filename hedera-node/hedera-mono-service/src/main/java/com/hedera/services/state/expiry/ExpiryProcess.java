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

import static com.hedera.services.state.tasks.SystemTaskResult.*;

import com.hedera.services.records.ConsensusTimeTracker;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.removal.RemovalWork;
import com.hedera.services.state.expiry.renewal.RenewalWork;
import com.hedera.services.state.tasks.SystemTask;
import com.hedera.services.state.tasks.SystemTaskResult;
import com.hedera.services.utils.EntityNum;
import java.time.Instant;
import javax.inject.Inject;

public class ExpiryProcess implements SystemTask {
    private final RenewalWork renewalWork;
    private final RemovalWork removalWork;
    private final ClassificationWork classifier;
    private final ConsensusTimeTracker consensusTimeTracker;

    @Inject
    public ExpiryProcess(
            final ClassificationWork classifier,
            final RenewalWork renewalWork,
            final RemovalWork removalWork,
            final ConsensusTimeTracker consensusTimeTracker) {
        this.consensusTimeTracker = consensusTimeTracker;
        this.renewalWork = renewalWork;
        this.removalWork = removalWork;
        this.classifier = classifier;
    }

    @Override
    public SystemTaskResult process(final long literalNum, final Instant now) {
        if (!consensusTimeTracker.hasMoreStandaloneRecordTime()) {
            return NEEDS_DIFFERENT_CONTEXT;
        }
        final var entityNum = EntityNum.fromLong(literalNum);
        final var result = classifier.classify(entityNum, now);
        return switch (result) {
            case COME_BACK_LATER -> NO_CAPACITY_LEFT;

            case EXPIRED_ACCOUNT_READY_TO_RENEW -> renewalWork.tryToRenewAccount(entityNum, now);
            case DETACHED_ACCOUNT_GRACE_PERIOD_OVER -> removalWork.tryToRemoveAccount(entityNum);

            case EXPIRED_CONTRACT_READY_TO_RENEW -> renewalWork.tryToRenewContract(entityNum, now);
            case DETACHED_CONTRACT_GRACE_PERIOD_OVER -> removalWork.tryToRemoveContract(entityNum);

            default -> NOTHING_TO_DO;
        };
    }
}
