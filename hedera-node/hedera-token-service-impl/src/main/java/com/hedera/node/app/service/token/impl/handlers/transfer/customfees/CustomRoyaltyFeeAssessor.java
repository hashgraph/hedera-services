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
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.CustomFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Assesses royalty fees for given token transfer. Royalty fees are only charged for NON_FUNGIBLE_TOKEN
 * type. If the nft has already paid royalty in any level of CryptoTransfer, it will not be charged
 * custom fees again.
 * If there is fungible exchange value to the receiver of the NFT,
 */
@Singleton
public class CustomRoyaltyFeeAssessor {

    private final CustomFixedFeeAssessor fixedFeeAssessor;

    @Inject
    public CustomRoyaltyFeeAssessor(final CustomFixedFeeAssessor fixedFeeAssessor) {
        this.fixedFeeAssessor = fixedFeeAssessor;
    }

    /**
     * Assesses royalty fees for given token transfer.
     * @param feeMeta
     * @param sender
     * @param receiver
     * @param result
     */
    // Suppressing the warning about using two "continue" statements and having unused variable
    @SuppressWarnings({"java:S1854", "java:S135"})
    public void assessRoyaltyFees(
            @NonNull final CustomFeeMeta feeMeta,
            @NonNull final AccountID sender,
            @NonNull final AccountID receiver,
            @NonNull final AssessmentResult result) {
        final var tokenId = feeMeta.tokenId();
        // In a given CryptoTransfer, we only charge royalties to an account once per token type; so
        // even if 0.0.A is sending multiple NFTs of type 0.0.T in a single transfer, we only deduct
        // royalty fees once from the value it receives in return.
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

    /**
     * Charges royalty fees to the receiver of the NFT. If the receiver is not receiving any fungible value
     * then the fallback fee is charged to the receiver balance. Otherwise, the royalty fee is charged to the
     * credit value of the receiver.
     * @param exchangedValues fungible values exchanged to the receiver
     * @param feeMeta custom fee meta
     * @param fee royalty fee
     * @param result assessment result
     */
    private void chargeRoyalty(
            @NonNull List<AdjustmentUtils.ExchangedValue> exchangedValues,
            @NonNull final CustomFeeMeta feeMeta,
            @NonNull final CustomFee fee,
            @NonNull final AssessmentResult result) {
        for (final var exchange : exchangedValues) {
            final var account = exchange.account();
            final var amount = exchange.amount();
            final var denom = exchange.tokenId();

            final var royaltySpec = fee.royaltyFeeOrThrow();
            final var feeCollector = fee.feeCollectorAccountIdOrThrow();

            final var royalty = safeFractionMultiply(
                    royaltySpec.exchangeValueFraction().numerator(),
                    royaltySpec.exchangeValueFraction().denominator(),
                    amount);
            validateTrue(royalty <= amount, INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);

            /* The id of the charging token is only used here to avoid recursively charging
            on fees charged in the units of their denominating token; but this is a credit,
            hence the id is irrelevant, and we can use null. */
            if (denom == null) {
                // exchange is for hbar
                adjustHbarFees(result, account, fee);
            } else {
                // exchange is for token
                adjustHtsFees(result, account, feeCollector, feeMeta, royalty, denom);
            }
            /* Note that this account has now paid all royalties for this NFT type */
            result.addToRoyaltiesPaid(Pair.of(account, denom));

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
