/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.state.expiry.renewal;

import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_CONTRACT;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_CONTRACT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.EXPIRED_CONTRACT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.RenewableEntityType.OTHER;
import static com.hedera.services.utils.EntityNum.fromAccountId;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper for renewing and removing expired entities. Only crypto accounts are supported in this
 * implementation.
 */
@Singleton
public class RenewableEntityClassifier {
    private static final Logger log = LogManager.getLogger(RenewableEntityClassifier.class);

    private final GlobalDynamicProperties dynamicProperties;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

    private EntityNum lastClassifiedNum;
    private MerkleAccount lastClassified = null;
    private EntityNum payerForAutoRenewNum;
    private MerkleAccount payerAccountForAutoRenew = null;

    @Inject
    public RenewableEntityClassifier(
            final GlobalDynamicProperties dynamicProperties,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts) {
        this.accounts = accounts;
        this.dynamicProperties = dynamicProperties;
    }

    public RenewableEntityType classify(final EntityNum candidateNum, final long now) {
        lastClassifiedNum = candidateNum;
        final var curAccounts = accounts.get();
        if (!curAccounts.containsKey(lastClassifiedNum)) {
            return OTHER;
        } else {
            lastClassified = curAccounts.get(lastClassifiedNum);
            final long expiry = lastClassified.getExpiry();
            if (expiry > now) {
                return OTHER;
            }
            final var isContract = lastClassified.isSmartContract();
            if (lastClassified.getBalance() > 0) {
                return isContract
                        ? EXPIRED_CONTRACT_READY_TO_RENEW
                        : EXPIRED_ACCOUNT_READY_TO_RENEW;
            }
            if (lastClassified.isDeleted()) {
                return isContract
                        ? DETACHED_CONTRACT_GRACE_PERIOD_OVER
                        : DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
            }
            final long gracePeriodEnd = expiry + dynamicProperties.autoRenewGracePeriod();
            if (gracePeriodEnd > now) {
                return isContract ? DETACHED_CONTRACT : DETACHED_ACCOUNT;
            }
            if (lastClassified.isTokenTreasury()) {
                return DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
            }
            return isContract
                    ? DETACHED_CONTRACT_GRACE_PERIOD_OVER
                    : DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
        }
    }

    public MerkleAccount getLastClassified() {
        return lastClassified;
    }

    public EntityNum getPayerForAutoRenew() {
        return payerForAutoRenewNum;
    }

    // --- Internal helpers ---
    void renewLastClassifiedWith(long fee, long renewalPeriod) {
        assertHasLastClassifiedAccount();
        assertPayerAccountForRenewalCanAfford(fee);

        final var currentAccounts = accounts.get();

        final var mutableLastClassified = currentAccounts.getForModify(lastClassifiedNum);
        final long newExpiry = mutableLastClassified.getExpiry() + renewalPeriod;
        mutableLastClassified.setExpiry(newExpiry);

        final var mutablePayerForRenew = currentAccounts.getForModify(payerForAutoRenewNum);
        final long newBalance = mutablePayerForRenew.getBalance() - fee;
        mutablePayerForRenew.setBalanceUnchecked(newBalance);

        final var fundingId = fromAccountId(dynamicProperties.fundingAccount());
        final var mutableFundingAccount = currentAccounts.getForModify(fundingId);
        final long newFundingBalance = mutableFundingAccount.getBalance() + fee;
        mutableFundingAccount.setBalanceUnchecked(newFundingBalance);

        log.debug("Renewed {} at a price of {}tb", lastClassifiedNum, fee);
    }

    /**
     * If there is an autoRenewAccount on the contract with non-zero hbar balance and not deleted,
     * uses that account for paying autorenewal fee. Else uses contract's hbar funds for renewal.
     *
     * @return resolved payer for renewal
     */
    MerkleAccount resolvePayerForAutoRenew() {
        if (lastClassified.isSmartContract() && lastClassified.hasAutoRenewAccount()) {
            payerForAutoRenewNum = lastClassified.getAutoRenewAccount().asNum();
            payerAccountForAutoRenew = accounts.get().get(payerForAutoRenewNum);
            if (isValid(payerAccountForAutoRenew)) {
                return payerAccountForAutoRenew;
            }
        }
        payerForAutoRenewNum = lastClassifiedNum;
        payerAccountForAutoRenew = lastClassified;
        return lastClassified;
    }

    /**
     * Checks if autoRenewAccount is not deleted and has non-zero hbar balance
     *
     * @param payer autoRenewAccount on contract
     * @return if the account is valid
     */
    boolean isValid(final MerkleAccount payer) {
        return payer != null && !payer.isDeleted() && payer.getBalance() > 0;
    }

    private void assertHasLastClassifiedAccount() {
        if (lastClassified == null) {
            throw new IllegalStateException(
                    "Cannot remove a last classified account; none is present!");
        }
    }

    private void assertPayerAccountForRenewalCanAfford(long fee) {
        if (payerAccountForAutoRenew.getBalance() < fee) {
            var msg = "Cannot charge " + fee + " to account number " + payerForAutoRenewNum + "!";
            throw new IllegalStateException(msg);
        }
    }
}
