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
package com.hedera.services.fees.charging;

import static com.hedera.services.exceptions.ValidationUtils.validateResourceLimit;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Responsible for charging the auto-renewal fee and storage rent from the payer or auto-renew
 * account if present on contracts and accounts. Distributes the charged fee between 0.0.800,
 * 0.0.801 and the funding account
 */
public class NonHapiFeeCharging {
    // Used to distribute charged fee to collection accounts in correct percentages
    final FeeDistribution feeDistribution;

    @Inject
    public NonHapiFeeCharging(final FeeDistribution feeDistribution) {
        this.feeDistribution = feeDistribution;
    }

    public void chargeNonHapiFee(
            @Nullable final EntityId preferredPayer,
            final AccountID finalPayer,
            final long fee,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts,
            final ResponseCodeEnum failureStatus) {
        var leftToPay = fee;

        if (preferredPayer != null && !MISSING_ENTITY_ID.equals(preferredPayer)) {
            final var grpcId = preferredPayer.toGrpcAccountId();
            if (accounts.contains(grpcId) && !(boolean) accounts.get(grpcId, IS_DELETED)) {
                final var debited =
                        charge(
                                preferredPayer.toGrpcAccountId(),
                                leftToPay,
                                false,
                                accounts,
                                failureStatus);
                leftToPay -= debited;
            }
        }
        if (leftToPay > 0) {
            charge(finalPayer, leftToPay, true, accounts, failureStatus);
        }
    }

    private long charge(
            final AccountID payer,
            final long amount,
            final boolean isLastResort,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts,
            final ResponseCodeEnum failureStatus) {
        long paid;
        final var balance = (long) accounts.get(payer, BALANCE);
        if (amount > balance) {
            validateResourceLimit(!isLastResort, failureStatus);
            accounts.set(payer, BALANCE, 0L);
            paid = balance;
        } else {
            accounts.set(payer, BALANCE, balance - amount);
            paid = amount;
        }
        feeDistribution.distributeChargedFee(paid, accounts);
        return paid;
    }
}
