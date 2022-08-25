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

import static com.hedera.services.state.expiry.EntityProcessResult.NOTHING_TO_DO;
import static com.hedera.services.utils.EntityNum.fromAccountId;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.state.expiry.EntityProcessResult;
import com.hedera.services.state.expiry.classification.ClassificationWork;
import com.hedera.services.state.expiry.classification.EntityLookup;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

@Singleton
public class RenewalHelper implements RenewalWork {
    private static final Logger log = LogManager.getLogger(RenewalHelper.class);
    private final ClassificationWork classifier;
    private final GlobalDynamicProperties properties;
    private final FeeCalculator fees;
    private final RenewalRecordsHelper recordsHelper;
    private final EntityLookup lookup;

    @Inject
    public RenewalHelper(
            final EntityLookup lookup,
            final ClassificationWork classifier,
            final GlobalDynamicProperties properties,
            final FeeCalculator fees,
            final RenewalRecordsHelper recordsHelper) {
        this.lookup = lookup;
        this.classifier = classifier;
        this.properties = properties;
        this.fees = fees;
        this.recordsHelper = recordsHelper;
    }

    @Nullable
    @Override
    public MerkleAccount tryToGetNextMutableExpiryCandidate() {
        return lookup.getMutableAccount(classifier.getLastClassifiedNum());
    }

    @Override
    public MerkleAccount getMutableAutoRenewPayer() {
        return lookup.getMutableAccount(classifier.getPayerNumForAutoRenew());
    }

    @Override
    public MerkleAccount getAutoRenewPayer() {
        return classifier.getPayerAccountForAutoRenew();
    }

    @Nullable
    @Override
    public MerkleAccount tryToGetNextExpiryCandidate() {
        return classifier.getLastClassified();
    }

    @Override
    public EntityProcessResult tryToRenewContract(
            final EntityNum contract, final Instant cycleTime) {
        if (!properties.shouldAutoRenewContracts()) {
            return NOTHING_TO_DO;
        }
        return renew(contract, cycleTime, true);
    }

    @Override
    public EntityProcessResult tryToRenewAccount(final EntityNum account, final Instant cycleTime) {
        if (!properties.shouldAutoRenewAccounts()) {
            return NOTHING_TO_DO;
        }
        return renew(account, cycleTime, false);
    }

    private EntityProcessResult renew(
            final EntityNum account, final Instant cycleTime, final boolean isContract) {
        final var payer = getAutoRenewPayer();
        final var expiringEntity = tryToGetNextExpiryCandidate();
        final var oldExpiry = expiringEntity.getExpiry();

        final long reqPeriod = expiringEntity.getAutoRenewSecs();
        final var assessment =
                fees.assessCryptoAutoRenewal(expiringEntity, reqPeriod, cycleTime, payer);
        final long renewalPeriod = assessment.renewalPeriod();
        final long renewalFee = assessment.fee();
        final var result = renewWith(renewalFee, renewalPeriod);

        recordsHelper.streamCryptoRenewal(
                account,
                renewalFee,
                oldExpiry + renewalPeriod,
                isContract,
                EntityNum.fromLong(payer.state().number()));
        return result;
    }

    public EntityProcessResult renewWith(long fee, long renewalPeriod) {
        assertHasLastClassifiedAccount();
        assertPayerAccountForRenewalCanAfford(fee);

        final var mutableEntity = tryToGetNextMutableExpiryCandidate();
        final long newExpiry = mutableEntity.getExpiry() + renewalPeriod;
        mutableEntity.setExpiry(newExpiry);

        final var mutablePayerForRenew = getMutableAutoRenewPayer();
        final long newBalance = mutablePayerForRenew.getBalance() - fee;
        mutablePayerForRenew.setBalanceUnchecked(newBalance);

        final var fundingAccount = properties.fundingAccount();
        final var fundingId = fromAccountId(fundingAccount);
        final var mutableFundingAccount = lookup.getMutableAccount(fundingId);
        final long newFundingBalance = mutableFundingAccount.getBalance() + fee;
        mutableFundingAccount.setBalanceUnchecked(newFundingBalance);

        log.debug("Renewed {} at a price of {}tb", classifier.getLastClassifiedNum(), fee);
        return EntityProcessResult.DONE;
    }

    private void assertHasLastClassifiedAccount() {
        if (classifier.getLastClassified() == null) {
            throw new IllegalStateException(
                    "Cannot remove a last classified account; none is present!");
        }
    }

    private void assertPayerAccountForRenewalCanAfford(long fee) {
        if (classifier.getPayerAccountForAutoRenew().getBalance() < fee) {
            var msg =
                    "Cannot charge "
                            + fee
                            + " to account number "
                            + classifier.getPayerNumForAutoRenew()
                            + "!";
            throw new IllegalStateException(msg);
        }
    }
}
