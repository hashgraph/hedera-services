/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.NEEDS_DIFFERENT_CONTEXT;
import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.NOTHING_TO_DO;
import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.NO_CAPACITY_LEFT;

import com.hedera.node.app.service.mono.records.ConsensusTimeTracker;
import com.hedera.node.app.service.mono.state.expiry.classification.ClassificationWork;
import com.hedera.node.app.service.mono.state.expiry.removal.RemovalWork;
import com.hedera.node.app.service.mono.state.expiry.renewal.RenewalWork;
import com.hedera.node.app.service.mono.state.tasks.SystemTask;
import com.hedera.node.app.service.mono.state.tasks.SystemTaskResult;
import com.hedera.node.app.service.mono.utils.EntityNum;
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

            case DETACHED_ACCOUNT -> removalWork.tryToMarkDetached(entityNum, false);
            case EXPIRED_ACCOUNT_READY_TO_RENEW -> renewalWork.tryToRenewAccount(entityNum, now);
            case DETACHED_ACCOUNT_GRACE_PERIOD_OVER -> removalWork.tryToRemoveAccount(entityNum);

            case DETACHED_CONTRACT -> removalWork.tryToMarkDetached(entityNum, true);
            case EXPIRED_CONTRACT_READY_TO_RENEW -> renewalWork.tryToRenewContract(entityNum, now);
            case DETACHED_CONTRACT_GRACE_PERIOD_OVER -> removalWork.tryToRemoveContract(entityNum);

            default -> NOTHING_TO_DO;
        };
    }
}
