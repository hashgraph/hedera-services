/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.adjustHbarFees;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.adjustHtsFees;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeExemptions.isPayerExempt;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.CustomFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Assesses fixed fees in a custom fee.
 * All the custom fees assessed from fixed fee will be added to the {@link AssessmentResult}
 * which will be used to create next level of transaction body to assess the next level of custom fees.
 * @see CustomFeeAssessor
 */
@Singleton
public class CustomFixedFeeAssessor {
    /**
     * Constructs a {@link CustomFixedFeeAssessor} instance.
     */
    @Inject
    public CustomFixedFeeAssessor() {
        // For Dagger injection
    }

    /**
     * Assesses fixed fees in a custom fee.
     * @param token the custom fee metadata for the token
     * @param sender the sender of the transaction, which will be payer for custom fees
     * @param result the assessment result which will be used to create next level of transaction body
     */
    public void assessFixedFees(
            @NonNull final Token token, @NonNull final AccountID sender, final AssessmentResult result) {
        for (final var fee : token.customFees()) {
            if (fee.fee().kind().equals(CustomFee.FeeOneOfType.FIXED_FEE)) {
                final var collector = fee.feeCollectorAccountId();
                if (sender.equals(collector)) {
                    continue;
                }
                // This is a top-level fixed fee, not a fallback royalty fee
                assessFixedFee(token, sender, fee, result);
            }
        }
    }

    /**
     * Fixed fee can be either a hbar or hts fee. This method assesses the fixed fee and adds the assessed fee to the
     * {@link AssessmentResult}.
     *
     * @param token  the custom fee metadata for the token
     * @param sender the sender of the transaction, which will be payer for custom fees
     * @param fee    the custom fee to be assessed
     * @param result the assessment result which will be used to create next level of transaction body
     */
    public void assessFixedFee(
            final Token token, final AccountID sender, final CustomFee fee, final AssessmentResult result) {
        if (isPayerExempt(token, fee, sender)) {
            return;
        }
        final var fixedFeeSpec = fee.fixedFeeOrThrow();
        if (!fixedFeeSpec.hasDenominatingTokenId()) {
            assessHbarFees(sender, fee, result);
        } else {
            assessHTSFees(sender, token, fee, result);
        }
    }

    /**
     * Assesses if the fixed fee is a hbar fee and adds the assessed fee debit and credit
     * to the {@link AssessmentResult}.
     * @param sender the sender of the transaction, which will be payer for custom fees
     * @param hbarFee the hbar fee to be assessed
     * @param result the assessment result which will be used to create next level of transaction body
     */
    private void assessHbarFees(
            @NonNull final AccountID sender, @NonNull final CustomFee hbarFee, @NonNull final AssessmentResult result) {
        final var collector = hbarFee.feeCollectorAccountId();
        final var fixedSpec = hbarFee.fixedFee();
        final var amount = fixedSpec.amount();

        adjustHbarFees(result, sender, hbarFee);
        // add all assessed fees for transaction record
        result.addAssessedCustomFee(AssessedCustomFee.newBuilder()
                .effectivePayerAccountId(sender)
                .amount(amount)
                .feeCollectorAccountId(collector)
                .build());
    }

    /**
     * Assesses if the fixed fee is a hts fee and adds the assessed fee debit and credit
     * to the {@link AssessmentResult}.
     * @param sender the sender of the transaction, which will be payer for custom fees
     * @param token token
     * @param htsFee the hts fee to be assessed
     * @param result the assessment result which will be used to create next level of transaction body
     */
    private void assessHTSFees(
            @NonNull final AccountID sender,
            @NonNull final Token token,
            @NonNull final CustomFee htsFee,
            @NonNull final AssessmentResult result) {
        final var collector = htsFee.feeCollectorAccountIdOrThrow();
        final var fixedFeeSpec = htsFee.fixedFeeOrThrow();
        final var amount = fixedFeeSpec.amount();
        final var denominatingToken = fixedFeeSpec.denominatingTokenIdOrThrow();
        adjustHtsFees(result, sender, collector, token, amount, denominatingToken);

        // add all assessed fees for transaction record
        result.addAssessedCustomFee(AssessedCustomFee.newBuilder()
                .effectivePayerAccountId(sender)
                .amount(amount)
                .feeCollectorAccountId(collector)
                .tokenId(fixedFeeSpec.denominatingTokenId())
                .build());
    }
}
