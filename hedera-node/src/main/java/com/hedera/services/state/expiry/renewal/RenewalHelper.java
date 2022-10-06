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
package com.hedera.services.state.expiry.renewal;

import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.state.tasks.SystemTaskResult.*;
import static com.hedera.services.throttling.MapAccessType.ACCOUNTS_GET_FOR_MODIFY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.charging.NonHapiFeeCharging;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.expiry.ExpiryRecordsHelper;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.tasks.SystemTaskResult;
import com.hedera.services.stats.ExpiryStats;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.MapAccessType;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RenewalHelper implements RenewalWork {
    static final List<MapAccessType> SELF_RENEWAL_WORK = List.of(ACCOUNTS_GET_FOR_MODIFY);
    static final List<MapAccessType> SUPPORTED_RENEWAL_WORK =
            List.of(ACCOUNTS_GET_FOR_MODIFY, ACCOUNTS_GET_FOR_MODIFY);

    private final ClassificationWork classifier;
    private final GlobalDynamicProperties dynamicProperties;
    private final FeeCalculator fees;
    private final ExpiryRecordsHelper recordsHelper;
    private final ExpiryThrottle expiryThrottle;
    private final ExpiryStats expiryStats;
    private final NonHapiFeeCharging nonHapiFeeCharging;
    private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    private final SideEffectsTracker sideEffectsTracker;

    @Inject
    public RenewalHelper(
            final ExpiryStats expiryStats,
            final ExpiryThrottle expiryThrottle,
            final ClassificationWork classifier,
            final GlobalDynamicProperties dynamicProperties,
            final FeeCalculator fees,
            final ExpiryRecordsHelper recordsHelper,
            final NonHapiFeeCharging nonHapiFeeCharging,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
            final SideEffectsTracker sideEffectsTracker) {
        this.expiryStats = expiryStats;
        this.expiryThrottle = expiryThrottle;
        this.classifier = classifier;
        this.dynamicProperties = dynamicProperties;
        this.fees = fees;
        this.recordsHelper = recordsHelper;
        this.nonHapiFeeCharging = nonHapiFeeCharging;
        this.accountsLedger = accountsLedger;
        this.sideEffectsTracker = sideEffectsTracker;
    }

    @Override
    public SystemTaskResult tryToRenewContract(final EntityNum contract, final Instant cycleTime) {
        if (!dynamicProperties.shouldAutoRenewContracts()) {
            return NOTHING_TO_DO;
        }
        return renew(contract, cycleTime, true);
    }

    @Override
    public SystemTaskResult tryToRenewAccount(final EntityNum account, final Instant cycleTime) {
        if (!dynamicProperties.shouldAutoRenewAccounts()) {
            return NOTHING_TO_DO;
        }
        return renew(account, cycleTime, false);
    }

    private SystemTaskResult renew(
            final EntityNum account, final Instant now, final boolean isContract) {
        assertHasLastClassifiedAccount();

        final var payer = classifier.getPayerForLastClassified();
        final var expired = classifier.getLastClassified();
        if (!expiryThrottle.allow(workFor(payer, expired))) {
            return NO_CAPACITY_LEFT;
        }

        final long reqPeriod = expired.getAutoRenewSecs();
        final var assessment = fees.assessCryptoAutoRenewal(expired, reqPeriod, now, payer);

        final long renewalPeriod = assessment.renewalPeriod();
        final long renewalFee = assessment.fee();

        sideEffectsTracker.reset();
        final var newExpiry = now.getEpochSecond() + renewalPeriod;
        renewWith(renewalFee, newExpiry);
        recordsHelper.streamCryptoRenewal(account, renewalFee, newExpiry, isContract);
        if (isContract) {
            expiryStats.countRenewedContract();
        }

        return DONE;
    }

    private List<MapAccessType> workFor(final MerkleAccount payer, final MerkleAccount expired) {
        return (payer == expired) ? SELF_RENEWAL_WORK : SUPPORTED_RENEWAL_WORK;
    }

    @VisibleForTesting
    void renewWith(final long fee, final long newExpiry) {
        assertPayerAccountForRenewalCanAfford(fee);

        final var lastClassifiedAccount = classifier.getLastClassifiedNum().toGrpcAccountId();
        final var payerForLastClassified =
                classifier.getPayerNumForLastClassified().toGrpcAccountId();

        accountsLedger.begin();
        accountsLedger.set(lastClassifiedAccount, EXPIRY, newExpiry);

        nonHapiFeeCharging.chargeNonHapiFee(
                EntityId.fromGrpcAccountId(payerForLastClassified),
                lastClassifiedAccount,
                fee,
                accountsLedger,
                INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES);

        accountsLedger.commit();
    }

    private void assertHasLastClassifiedAccount() {
        if (classifier.getLastClassified() == null) {
            throw new IllegalStateException(
                    "Cannot remove a last classified account; none is present!");
        }
    }

    private void assertPayerAccountForRenewalCanAfford(long fee) {
        if (classifier.getPayerForLastClassified().getBalance() < fee) {
            var msg =
                    "Cannot charge "
                            + fee
                            + " to account "
                            + classifier.getPayerNumForLastClassified().toIdString()
                            + "!";
            throw new IllegalStateException(msg);
        }
    }
}
