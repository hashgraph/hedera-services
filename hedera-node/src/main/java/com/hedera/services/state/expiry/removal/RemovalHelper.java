/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.expiry.removal;

import static com.hedera.services.state.expiry.EntityProcessResult.DONE;
import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;
import static com.hedera.services.state.expiry.EntityProcessResult.STILL_MORE_TO_DO;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.state.expiry.ExpiryRecordsHelper;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.utils.EntityNum;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RemovalHelper implements RemovalWork {
    private final ClassificationWork classifier;
    private final GlobalDynamicProperties properties;
    private final ContractGC contractGC;
    private final AccountGC accountGC;
    private final ExpiryRecordsHelper recordsHelper;

    @Inject
    public RemovalHelper(
            final ClassificationWork classifier,
            final GlobalDynamicProperties properties,
            final ContractGC contractGC,
            final AccountGC accountGC,
            final ExpiryRecordsHelper recordsHelper) {
        this.classifier = classifier;
        this.properties = properties;
        this.contractGC = contractGC;
        this.accountGC = accountGC;
        this.recordsHelper = recordsHelper;
    }

    @Override
    public EntityProcessResult tryToRemoveAccount(final EntityNum account) {
        if (!properties.shouldAutoRenewAccounts()) {
            return NOTHING_TO_DO;
        }
        return remove(account, false);
    }

    @Override
    public EntityProcessResult tryToRemoveContract(final EntityNum contract) {
        if (!properties.shouldAutoRenewContracts()) {
            return NOTHING_TO_DO;
        }
        return remove(contract, true);
    }

    private EntityProcessResult remove(final EntityNum num, final boolean isContract) {
        final var lastClassified = classifier.getLastClassified();
        if (isContract && !contractGC.expireBestEffort(num, lastClassified)) {
            return STILL_MORE_TO_DO;
        }
        final var gcOutcome = accountGC.expireBestEffort(num, lastClassified);
        if (gcOutcome.needsExternalizing()) {
            final var autoRenewId = isContract ? lastClassified.getAutoRenewAccount() : null;
            recordsHelper.streamCryptoRemovalStep(isContract, num, autoRenewId, gcOutcome);
        }
        return gcOutcome.finished() ? DONE : STILL_MORE_TO_DO;
    }
}
