/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.expiry.removal;

import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.DONE;
import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.NOTHING_TO_DO;
import static com.hedera.node.app.service.mono.state.tasks.SystemTaskResult.NO_CAPACITY_LEFT;
import static com.hedera.node.app.service.mono.throttling.MapAccessType.ACCOUNTS_GET_FOR_MODIFY;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.state.expiry.ExpiryRecordsHelper;
import com.hedera.node.app.service.mono.state.expiry.classification.ClassificationWork;
import com.hedera.node.app.service.mono.state.tasks.SystemTaskResult;
import com.hedera.node.app.service.mono.stats.ExpiryStats;
import com.hedera.node.app.service.mono.throttling.ExpiryThrottle;
import com.hedera.node.app.service.mono.utils.EntityNum;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RemovalHelper implements RemovalWork {
    private final ClassificationWork classifier;
    private final GlobalDynamicProperties properties;
    private final ContractGC contractGC;
    private final AccountGC accountGC;
    private final ExpiryRecordsHelper recordsHelper;
    private final ExpiryStats expiryStats;
    private final ExpiryThrottle expiryThrottle;

    @Inject
    public RemovalHelper(
            final ExpiryStats expiryStats,
            final ClassificationWork classifier,
            final GlobalDynamicProperties properties,
            final ContractGC contractGC,
            final AccountGC accountGC,
            final ExpiryRecordsHelper recordsHelper,
            final ExpiryThrottle expiryThrottle) {
        this.expiryStats = expiryStats;
        this.classifier = classifier;
        this.properties = properties;
        this.contractGC = contractGC;
        this.accountGC = accountGC;
        this.recordsHelper = recordsHelper;
        this.expiryThrottle = expiryThrottle;
    }

    @Override
    public SystemTaskResult tryToMarkDetached(final EntityNum num, final boolean isContract) {
        if (nothingToDoForDetached(isContract)) {
            return NOTHING_TO_DO;
        }
        if (!expiryThrottle.allowOne(ACCOUNTS_GET_FOR_MODIFY)) {
            return NO_CAPACITY_LEFT;
        }
        accountGC.markDetached(num);
        return DONE;
    }

    @Override
    public SystemTaskResult tryToRemoveAccount(final EntityNum account) {
        if (!properties.shouldAutoRenewAccounts()) {
            return NOTHING_TO_DO;
        }
        return remove(account, false);
    }

    @Override
    public SystemTaskResult tryToRemoveContract(final EntityNum contract) {
        if (!properties.shouldAutoRenewContracts()) {
            return NOTHING_TO_DO;
        }
        return remove(contract, true);
    }

    private SystemTaskResult remove(final EntityNum num, final boolean isContract) {
        final var lastClassified = classifier.getLastClassified();
        if (isContract && !contractGC.expireBestEffort(num, lastClassified)) {
            return NO_CAPACITY_LEFT;
        }
        final var gcOutcome = accountGC.expireBestEffort(num, lastClassified);
        if (gcOutcome.needsExternalizing()) {
            recordsHelper.streamCryptoRemovalStep(isContract, num, gcOutcome);
        }
        if (gcOutcome.finished()) {
            if (isContract) {
                expiryStats.countRemovedContract();
            }
            return DONE;
        } else {
            return NO_CAPACITY_LEFT;
        }
    }

    private boolean nothingToDoForDetached(final boolean isContract) {
        return (isContract && !properties.shouldAutoRenewContracts()) || !properties.shouldAutoRenewAccounts();
    }
}
