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
package com.hedera.services.state.expiry.classification;

import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_CONTRACT;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_CONTRACT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.classification.ClassificationResult.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.classification.ClassificationResult.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.classification.ClassificationResult.EXPIRED_CONTRACT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.classification.ClassificationResult.OTHER;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Helper for renewing and removing expired entities. Only crypto accounts are supported in this
 * implementation.
 */
@Singleton
public class ClassificationWork {
    private final GlobalDynamicProperties dynamicProperties;
    private final EntityLookup lookup;
    private EntityNum lastClassifiedNum;
    private MerkleAccount lastClassified = null;
    private EntityNum payerForAutoRenewNum;
    private MerkleAccount payerAccountForAutoRenew = null;

    @Inject
    public ClassificationWork(
            final GlobalDynamicProperties dynamicProperties, final EntityLookup lookup) {
        this.dynamicProperties = dynamicProperties;
        this.lookup = lookup;
    }

    public ClassificationResult classify(final EntityNum candidateNum, final long now) {
        lastClassifiedNum = candidateNum;
        if (!lookup.accountsContainsKey(lastClassifiedNum)) {
            return OTHER;
        } else {
            lastClassified = lookup.getImmutableAccount(lastClassifiedNum);
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

    public EntityNum getLastClassifiedNum() {
        return lastClassifiedNum;
    }

    public EntityNum getPayerNumForAutoRenew() {
        return payerForAutoRenewNum;
    }

    public MerkleAccount getPayerAccountForAutoRenew() {
        return payerAccountForAutoRenew;
    }

    /**
     * If there is an autoRenewAccount on the contract with non-zero hbar balance and not deleted,
     * uses that account for paying autorenewal fee. Else uses contract's hbar funds for renewal.
     *
     * @return resolved payer for renewal
     */
    public MerkleAccount resolvePayerForAutoRenew() {
        if (lastClassified.isSmartContract() && lastClassified.hasAutoRenewAccount()) {
            payerForAutoRenewNum = lastClassified.getAutoRenewAccount().asNum();
            payerAccountForAutoRenew = lookup.getImmutableAccount(payerForAutoRenewNum);
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
}
