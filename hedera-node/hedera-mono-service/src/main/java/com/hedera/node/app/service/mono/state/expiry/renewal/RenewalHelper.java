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
package com.hedera.node.app.service.mono.state.expiry.renewal;

import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.node.app.service.mono.throttling.MapAccessType.ACCOUNTS_GET_FOR_MODIFY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_BALANCES_FOR_RENEWAL_FEES;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.charging.NonHapiFeeCharging;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.expiry.ExpiryRecordsHelper;
import com.hedera.node.app.service.mono.state.expiry.classification.ClassificationWork;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.tasks.SystemTaskResult;
import com.hedera.node.app.service.mono.stats.ExpiryStats;
import com.hedera.node.app.service.mono.throttling.ExpiryThrottle;
import com.hedera.node.app.service.mono.throttling.MapAccessType;
import com.hedera.node.app.service.mono.utils.EntityNum;
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
    private final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger;
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
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger,
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
            return SystemTaskResult.NOTHING_TO_DO;
        }
        return renew(contract, cycleTime, true);
    }

    @Override
    public SystemTaskResult tryToRenewAccount(final EntityNum account, final Instant cycleTime) {
        if (!dynamicProperties.shouldAutoRenewAccounts()) {
            return SystemTaskResult.NOTHING_TO_DO;
        }
        return renew(account, cycleTime, false);
    }

    private SystemTaskResult renew(
            final EntityNum account, final Instant now, final boolean isContract) {
        assertHasLastClassifiedAccount();

        final var payer = classifier.getPayerForLastClassified();
        final var expired = classifier.getLastClassified();
        if (!expiryThrottle.allow(workFor(payer, expired))) {
            return SystemTaskResult.NO_CAPACITY_LEFT;
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

        return SystemTaskResult.DONE;
    }

    private List<MapAccessType> workFor(final HederaAccount payer, final HederaAccount expired) {
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
        accountsLedger.set(lastClassifiedAccount, EXPIRED_AND_PENDING_REMOVAL, false);

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
