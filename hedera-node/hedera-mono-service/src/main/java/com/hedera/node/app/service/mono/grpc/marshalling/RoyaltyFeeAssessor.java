/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.grpc.marshalling;

import static com.hedera.node.app.service.mono.grpc.marshalling.FeeAssessor.IS_FALLBACK_FEE;
import static com.hedera.node.app.service.mono.state.submerkle.FcCustomFee.FeeType.ROYALTY_FEE;
import static com.hedera.node.app.service.mono.store.models.Id.MISSING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.fees.CustomFeePayerExemptions;
import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.state.submerkle.RoyaltyFeeSpec;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.List;

public class RoyaltyFeeAssessor {
    private final FixedFeeAssessor fixedFeeAssessor;
    private final FungibleAdjuster fungibleAdjuster;
    private final CustomFeePayerExemptions customFeePayerExemptions;

    public RoyaltyFeeAssessor(
            final FixedFeeAssessor fixedFeeAssessor,
            final FungibleAdjuster fungibleAdjuster,
            final CustomFeePayerExemptions customFeePayerExemptions) {
        this.fixedFeeAssessor = fixedFeeAssessor;
        this.fungibleAdjuster = fungibleAdjuster;
        this.customFeePayerExemptions = customFeePayerExemptions;
    }

    public ResponseCodeEnum assessAllRoyalties(
            final BalanceChange change,
            final CustomFeeMeta customFeeMeta,
            final BalanceChangeManager changeManager,
            final List<AssessedCustomFeeWrapper> accumulator) {
        if (!change.isForNft()) {
            /* This change was denominated in a non-fungible token type---but appeared
             * in the fungible transfer list. Fail now with the appropriate status. */
            return ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
        }

        final var payer = change.getAccount();
        final var token = change.getToken();

        /* If the same account sends multiple NFTs of the same type in the
        same transfer, we only charge the royalties once. */
        if (changeManager.isRoyaltyPaid(token, payer)) {
            return OK;
        }

        final var exchangedValue = changeManager.fungibleCreditsInCurrentLevel(payer);
        for (var fee : customFeeMeta.customFees()) {
            final var collector = fee.getFeeCollectorAsId();
            if (fee.getFeeType() != ROYALTY_FEE) {
                continue;
            }
            final var spec = fee.getRoyaltyFeeSpec();

            if (exchangedValue.isEmpty()) {
                final var fallback = spec.fallbackFee();
                if (fallback != null) {
                    // A NFT transfer with royalty fees to an unknown alias is not possible, since
                    // the auto-created
                    // account will not have any hbar to pay the fallback fee
                    if (change.hasNonEmptyCounterPartyAlias()) {
                        return INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
                    }
                    final var receiver = Id.fromGrpcAccount(change.counterPartyAccountId());
                    final var fallbackFee =
                            FcCustomFee.fixedFee(
                                    fallback.getUnitsToCollect(),
                                    fallback.getTokenDenomination(),
                                    collector.asEntityId(),
                                    fee.getAllCollectorsAreExempt());
                    fixedFeeAssessor.assess(
                            receiver,
                            customFeeMeta,
                            fallbackFee,
                            changeManager,
                            accumulator,
                            IS_FALLBACK_FEE);
                }
            } else if (!customFeePayerExemptions.isPayerExempt(customFeeMeta, fee, payer)) {
                final var fractionalValidity =
                        chargeRoyalty(
                                collector,
                                spec,
                                exchangedValue,
                                fungibleAdjuster,
                                changeManager,
                                accumulator);
                if (fractionalValidity != OK) {
                    return fractionalValidity;
                }
            }
        }

        /* Note that this account has now paid all royalties for this NFT type */
        changeManager.markRoyaltyPaid(token, payer);
        return OK;
    }

    private ResponseCodeEnum chargeRoyalty(
            final Id collector,
            final RoyaltyFeeSpec spec,
            final List<BalanceChange> exchangedValue,
            final FungibleAdjuster fungibleAdjuster,
            final BalanceChangeManager changeManager,
            final List<AssessedCustomFeeWrapper> accumulator) {
        for (var exchange : exchangedValue) {
            long value = exchange.originalUnits();
            long royaltyFee =
                    AdjustmentUtils.safeFractionMultiply(
                            spec.numerator(), spec.denominator(), value);
            if (exchange.getAggregatedUnits() < royaltyFee) {
                return INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
            }
            exchange.aggregateUnits(-royaltyFee);
            final var denom = exchange.isForHbar() ? MISSING_ID : exchange.getToken();
            /* The id of the charging token is only used here to avoid recursively charging
            on fees charged in the units of their denominating token; but this is a credit,
            hence the id is irrelevant and we can use MISSING_ID. */
            fungibleAdjuster.adjustedChange(
                    collector, MISSING_ID, denom, royaltyFee, changeManager);
            final var effPayerAccountNum = new AccountID[] {exchange.getAccount().asGrpcAccount()};
            final var collectorId = collector.asEntityId();
            final var assessed =
                    exchange.isForHbar()
                            ? new AssessedCustomFeeWrapper(
                                    collectorId, royaltyFee, effPayerAccountNum)
                            : new AssessedCustomFeeWrapper(
                                    collectorId,
                                    denom.asEntityId(),
                                    royaltyFee,
                                    effPayerAccountNum);
            accumulator.add(assessed);
        }
        return OK;
    }
}
