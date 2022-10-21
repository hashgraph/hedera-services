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

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.migration.HederaAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Distributes an already-charged fee to collection accounts in the correct percentages.
 *
 * <p>Properties {@code ledger.fundingAccount}, {@code accounts.stakingRewardAccount}, and {@code
 * accounts.nodeRewardAccount} give the numbers of the three collection accounts. The {@code
 * staking.fees.stakingRewardPercentage} and {@code staking.fees.nodeRewardPercentage} properties
 * give the percentages distributed to the reward accounts. The funding account receives any
 * left-over.
 */
@Singleton
public class FeeDistribution {
    private final GlobalDynamicProperties dynamicProperties;
    private final AccountID stakingRewardAccountId;
    private final AccountID nodeRewardAccountId;

    @Inject
    public FeeDistribution(
            final AccountNumbers accountNumbers, final GlobalDynamicProperties dynamicProperties) {
        this.dynamicProperties = dynamicProperties;
        this.stakingRewardAccountId =
                STATIC_PROPERTIES.scopedAccountWith(accountNumbers.stakingRewardAccount());
        this.nodeRewardAccountId =
                STATIC_PROPERTIES.scopedAccountWith(accountNumbers.nodeRewardAccount());
    }

    /**
     * Distributes a given amount to the collection accounts in the appropriate percentages.
     * <b>IMPORTANT:</b> the caller is responsible for deducting this amount from a payer account in
     * the given ledger; it will otherwise throw {@link
     * com.hedera.services.exceptions.InconsistentAdjustmentsException} in {@link
     * TransactionalLedger#commit()}.
     *
     * <p>Callers can also use this method with a negative {@code amount} to handle refunds.
     *
     * @param amount the amount to distribute to fee collectors
     * @param accounts the ledger to use for the distribution
     */
    public void distributeChargedFee(
            final long amount,
            final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accounts) {
        long fundingAdjustment = amount;
        final var fundingId = dynamicProperties.fundingAccount();
        if (dynamicProperties.isStakingEnabled()) {
            final var nodeRewardAdjustment = nodeRewardFractionOf(amount);
            if (nodeRewardAdjustment != 0) {
                final var nodeRewardBalance = (long) accounts.get(nodeRewardAccountId, BALANCE);
                accounts.set(
                        nodeRewardAccountId, BALANCE, nodeRewardBalance + nodeRewardAdjustment);
            }
            final var stakeRewardAdjustment = stakingRewardFractionOf(amount);
            if (stakeRewardAdjustment != 0) {
                final var stakeRewardBalance = (long) accounts.get(stakingRewardAccountId, BALANCE);
                accounts.set(
                        stakingRewardAccountId,
                        BALANCE,
                        stakeRewardBalance + stakeRewardAdjustment);
            }
            fundingAdjustment -= (nodeRewardAdjustment + stakeRewardAdjustment);
        }
        if (fundingAdjustment != 0) {
            final var fundingBalance = (long) accounts.get(fundingId, BALANCE);
            accounts.set(fundingId, BALANCE, fundingBalance + fundingAdjustment);
        }
    }

    private long stakingRewardFractionOf(final long totalFee) {
        return (dynamicProperties.getStakingRewardPercent() * totalFee) / 100;
    }

    private long nodeRewardFractionOf(final long totalFee) {
        return (dynamicProperties.getNodeRewardPercent() * totalFee) / 100;
    }
}
