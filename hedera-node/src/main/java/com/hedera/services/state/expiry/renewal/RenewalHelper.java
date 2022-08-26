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

import static com.hedera.services.state.expiry.EntityProcessResult.*;
import static com.hedera.services.throttling.MapAccessType.ACCOUNTS_GET_FOR_MODIFY;
import static com.hedera.services.utils.EntityNum.fromAccountId;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.state.expiry.ExpiryRecordsHelper;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.MapAccessType;
import com.hedera.services.utils.EntityNum;
import java.time.Instant;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class RenewalHelper implements RenewalWork {
    private static final Logger log = LogManager.getLogger(RenewalHelper.class);

    static final List<MapAccessType> SELF_RENEWAL_WORK = List.of(ACCOUNTS_GET_FOR_MODIFY);
    static final List<MapAccessType> SUPPORTED_RENEWAL_WORK =
            List.of(ACCOUNTS_GET_FOR_MODIFY, ACCOUNTS_GET_FOR_MODIFY);

    private final ClassificationWork classifier;
    private final GlobalDynamicProperties dynamicProperties;
    private final FeeCalculator fees;
    private final ExpiryRecordsHelper recordsHelper;
    private final EntityLookup lookup;
    private final ExpiryThrottle expiryThrottle;

    @Inject
    public RenewalHelper(
            final EntityLookup lookup,
            final ExpiryThrottle expiryThrottle,
            final ClassificationWork classifier,
            final GlobalDynamicProperties dynamicProperties,
            final FeeCalculator fees,
            final ExpiryRecordsHelper recordsHelper) {
        this.lookup = lookup;
        this.expiryThrottle = expiryThrottle;
        this.classifier = classifier;
        this.dynamicProperties = dynamicProperties;
        this.fees = fees;
        this.recordsHelper = recordsHelper;
    }

    @Override
    public EntityProcessResult tryToRenewContract(
            final EntityNum contract, final Instant cycleTime) {
        if (!dynamicProperties.shouldAutoRenewContracts()) {
            return NOTHING_TO_DO;
        }
        return renew(contract, cycleTime, true);
    }

    @Override
    public EntityProcessResult tryToRenewAccount(final EntityNum account, final Instant cycleTime) {
        if (!dynamicProperties.shouldAutoRenewAccounts()) {
            return NOTHING_TO_DO;
        }
        return renew(account, cycleTime, false);
    }

    private EntityProcessResult renew(
            final EntityNum account, final Instant cycleTime, final boolean isContract) {
        assertHasLastClassifiedAccount();

        final var payer = classifier.getPayerForLastClassified();
        final var expired = classifier.getLastClassified();
        if (!expiryThrottle.allow(workFor(payer, expired))) {
            return STILL_MORE_TO_DO;
        }

        final long reqPeriod = expired.getAutoRenewSecs();
        final var assessment = fees.assessCryptoAutoRenewal(expired, reqPeriod, cycleTime, payer);

        final long renewalPeriod = assessment.renewalPeriod();
        final long renewalFee = assessment.fee();
        final var oldExpiry = expired.getExpiry();
        renewWith(renewalFee, renewalPeriod);

        recordsHelper.streamCryptoRenewal(
                account, renewalFee, oldExpiry + renewalPeriod, isContract, payer.getKey());
        return DONE;
    }

    private List<MapAccessType> workFor(final MerkleAccount payer, final MerkleAccount expired) {
        return (payer == expired) ? SELF_RENEWAL_WORK : SUPPORTED_RENEWAL_WORK;
    }

    @VisibleForTesting
    void renewWith(long fee, long renewalPeriod) {
        assertPayerAccountForRenewalCanAfford(fee);

        final var mutableAccount = lookup.getMutableAccount(classifier.getLastClassifiedNum());
        final long newExpiry = mutableAccount.getExpiry() + renewalPeriod;
        mutableAccount.setExpiry(newExpiry);

        final var mutablePayerForRenew =
                lookup.getMutableAccount(classifier.getPayerNumForLastClassified());
        final long newBalance = mutablePayerForRenew.getBalance() - fee;
        mutablePayerForRenew.setBalanceUnchecked(newBalance);

        final var fundingAccount = dynamicProperties.fundingAccount();
        final var fundingId = fromAccountId(fundingAccount);
        final var mutableFundingAccount = lookup.getMutableAccount(fundingId);
        final long newFundingBalance = mutableFundingAccount.getBalance() + fee;
        mutableFundingAccount.setBalanceUnchecked(newFundingBalance);

        log.debug("Renewed {} at a price of {}tb", classifier.getLastClassifiedNum(), fee);
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
