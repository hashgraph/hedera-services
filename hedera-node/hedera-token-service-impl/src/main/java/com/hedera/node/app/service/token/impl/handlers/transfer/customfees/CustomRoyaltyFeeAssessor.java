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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.adjustHbarFees;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.adjustHtsFees;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.asFixedFee;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.getFungibleCredits;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.safeFractionMultiply;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeExemptions.isPayerExempt;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import javax.inject.Inject;
import org.apache.commons.lang3.tuple.Pair;

public class CustomRoyaltyFeeAssessor {
    private CustomFixedFeeAssessor fixedFeeAssessor;

    @Inject
    public CustomRoyaltyFeeAssessor(final CustomFixedFeeAssessor fixedFeeAssessor) {
        this.fixedFeeAssessor = fixedFeeAssessor;
    }

    public void assessRoyaltyFees(
            @NonNull final CustomFeeMeta feeMeta,
            @NonNull final AccountID sender,
            @NonNull final AccountID receiver,
            @NonNull final AssessmentResult result,
            @NonNull final HandleContext handleContext) {
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);

        final var tokenId = feeMeta.tokenId();
        if (result.getRoyaltiesPaid().contains(Pair.of(sender, tokenId))) {
            return;
        }

        // get all hbar and fungible token changes from given input to the current level
        final var exchangedValue = getFungibleCredits(result, tokenId, sender);
        for (final var fee : feeMeta.customFees()) {
            final var collector = fee.feeCollectorAccountId();
            if (!fee.fee().kind().equals(CustomFee.FeeOneOfType.ROYALTY_FEE)) {
                continue;
            }
            final var royaltyFee = fee.royaltyFeeOrThrow();
            // If there are no fungible units to the receiver, then  if there is a fallback fee
            // then receiver should pay the fallback fee
            if (exchangedValue.isEmpty()) {
                if (!royaltyFee.hasFallbackFee()) {
                    continue;
                }
                final var fallback = royaltyFee.fallbackFeeOrThrow();
                // A NFT transfer with royalty fees to an unknown alias is not possible, since
                // the auto-created account will not have any hbar to pay the fallback fee
                // Validate the account balance is greater than zero
                validateTrue(
                        accountStore.get(receiver).tinybarBalance() != 0,
                        INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);
                final var fallbackFee = asFixedFee(
                        fallback.amount(), fallback.denominatingTokenId(), collector, fee.allCollectorsAreExempt());
                fixedFeeAssessor.assessFixedFee(feeMeta, receiver, fallbackFee, result);
            } else {
                if (!isPayerExempt(feeMeta, fee, sender)) {
                    chargeRoyalty(exchangedValue, feeMeta, fee, result);
                }
            }
        }
    }

    private void chargeRoyalty(
            @NonNull Map<AccountID, Pair<Long, TokenID>> exchangedValues,
            @NonNull final CustomFeeMeta feeMeta,
            @NonNull final CustomFee fee,
            @NonNull final AssessmentResult result) {
        for (final var exchange : exchangedValues.entrySet()) {
            final var account = exchange.getKey();
            final var value = exchange.getValue();
            final var amount = value.getLeft();
            final var tokenId = value.getRight();

            final var royaltySpec = fee.royaltyFeeOrThrow();
            final var feeCollector = fee.feeCollectorAccountIdOrThrow();

            final var royalty = safeFractionMultiply(
                    royaltySpec.exchangeValueFraction().numerator(),
                    royaltySpec.exchangeValueFraction().denominator(),
                    amount);
            validateTrue(royalty <= amount, INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);

            final var denom = tokenId == null ? null : tokenId;
            /* The id of the charging token is only used here to avoid recursively charging
            on fees charged in the units of their denominating token; but this is a credit,
            hence the id is irrelevant and we can use MISSING_ID. */
            if (denom == null) {
                // exchange is for hbar
                adjustHbarFees(result, account, fee);
            } else {
                // exchange is for token
                adjustHtsFees(
                        result.getHtsAdjustments(),
                        account,
                        feeCollector,
                        feeMeta,
                        amount,
                        denom,
                        result.getExemptDebits());
            }
            /* Note that this account has now paid all royalties for this NFT type */
            result.addToRoyaltiesPaid(Pair.of(account, tokenId));

            final var assessedCustomFeeBuilder = AssessedCustomFee.newBuilder()
                    .amount(royalty)
                    .feeCollectorAccountId(feeCollector)
                    .effectivePayerAccountId(account);
            if (denom == null) {
                // exchange is for hbar
                result.addAssessedCustomFee(assessedCustomFeeBuilder.build());
            } else {
                // exchange is for token
                result.addAssessedCustomFee(
                        assessedCustomFeeBuilder.tokenId(denom).build());
            }
        }
    }
}
