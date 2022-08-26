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

import static com.hedera.services.state.expiry.classification.ClassificationResult.*;
import static com.hedera.services.throttling.MapAccessType.ACCOUNTS_GET;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.MapAccessType;
import com.hedera.services.utils.EntityNum;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Helper for renewing and removing expired entities. Only crypto accounts are supported in this
 * implementation.
 */
@Singleton
public class ClassificationWork {
    public static final List<MapAccessType> CLASSIFICATION_WORK = List.of(ACCOUNTS_GET);

    private final GlobalDynamicProperties dynamicProperties;
    private final EntityLookup lookup;
    private final ExpiryThrottle expiryThrottle;
    private EntityNum lastClassifiedNum;
    private MerkleAccount lastClassified;
    private EntityNum payerNum;
    private MerkleAccount payer;

    @Inject
    public ClassificationWork(
            final GlobalDynamicProperties dynamicProperties,
            final EntityLookup lookup,
            final ExpiryThrottle expiryThrottle) {
        this.dynamicProperties = dynamicProperties;
        this.expiryThrottle = expiryThrottle;
        this.lookup = lookup;
    }

    public ClassificationResult classify(final EntityNum candidateNum, final Instant now) {
        if (!expiryThrottle.allow(CLASSIFICATION_WORK, now)) {
            return COME_BACK_LATER;
        }

        payer = null;
        payerNum = null;
        lastClassified = null;
        lastClassifiedNum = candidateNum;

        lastClassified = lookup.getImmutableAccount(lastClassifiedNum);
        if (lastClassified == null) {
            return OTHER;
        } else {
            final var longNow = now.getEpochSecond();
            final long expiry = lastClassified.getExpiry();
            if (expiry > longNow) {
                return OTHER;
            }

            final var isContract = lastClassified.isSmartContract();
            if (lastClassified.isDeleted()) {
                return isContract
                        ? DETACHED_CONTRACT_GRACE_PERIOD_OVER
                        : DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
            }
            if (isContract) {
                if (!expiryThrottle.allow(CLASSIFICATION_WORK)) {
                    return COME_BACK_LATER;
                }
                resolveClassifiedContractPayer();
            } else {
                payer = lastClassified;
                payerNum = lastClassifiedNum;
            }

            if (payer.getBalance() > 0) {
                return isContract
                        ? EXPIRED_CONTRACT_READY_TO_RENEW
                        : EXPIRED_ACCOUNT_READY_TO_RENEW;
            }

            // The effective payer for the expired crypto account has zero balance
            final long gracePeriodEnd = expiry + dynamicProperties.autoRenewGracePeriod();
            if (gracePeriodEnd > longNow) {
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

    public EntityNum getLastClassifiedNum() {
        return lastClassifiedNum;
    }

    public MerkleAccount getLastClassified() {
        return lastClassified;
    }

    public EntityNum getPayerNumForLastClassified() {
        return payerNum;
    }

    public MerkleAccount getPayerForLastClassified() {
        return payer;
    }

    private void resolveClassifiedContractPayer() {
        if (lastClassified.hasAutoRenewAccount()) {
            payerNum = Objects.requireNonNull(lastClassified.getAutoRenewAccount()).asNum();
            payer = lookup.getImmutableAccount(payerNum);
            if (isValid(payer)) {
                return;
            }
        }
        payerNum = lastClassifiedNum;
        payer = lastClassified;
    }

    @VisibleForTesting
    boolean isValid(final MerkleAccount payer) {
        return payer != null && !payer.isDeleted() && payer.getBalance() > 0;
    }
}
