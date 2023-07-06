/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;

import com.hedera.node.app.service.mono.grpc.marshalling.BalanceChangeManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.Nullable;

public class AdjustmentUtils {
    private AdjustmentUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static TransactionBody.Builder adjustedChange(
            final AccountID account,
            final TokenID chargingToken,
            final TokenID denom,
            final long amount,
            final BalanceChangeManager manager,
            final TransactionBody.Builder builder) {
        /* Always append a new change for an HTS debit since it could trigger another assessed fee */
        if (denom != null && amount < 0) {
            final var htsDebit = includedHtsChange(account, denom, amount, builder);
            /* But self-denominated fees are exempt from further custom fee charging,
            c.f. https://github.com/hashgraph/hedera-services/issues/1925 */
            if (chargingToken.equals(denom)) {
                htsDebit.setExemptFromCustomFees(true);
            }
            return htsDebit;
        }

        /* Otherwise, just update the existing change for this account denomination if present */
        final var extantChange = manager.changeFor(account, denom);
        if (extantChange == null) {
            if (denom == MISSING_ID) {
                final var newHbarChange = BalanceChange.hbarCustomFeeAdjust(account, amount);
                manager.includeChange(newHbarChange);
                return newHbarChange;
            } else {
                return includedHtsChange(account, denom, amount, manager);
            }
        } else {
            extantChange.aggregateUnits(amount);
            return extantChange;
        }
    }

    static void adjustForAssessedHbar(
            final AccountID sender,
            final Id collector,
            final long amount,
            final BalanceChangeManager manager,
            final boolean isFallbackFee) {
        adjustForAssessed(payer, MISSING_ID, collector, MISSING_ID, amount, manager, isFallbackFee);
    }

    /**
     * Examines a custom fee (that is, an {@code amount} of a {@code denom} token being charged by a
     * {@code chargingToken}); and updates the balance changes for its payer and collector in the
     * given {@link BalanceChangeManager}.
     *
     * <p>If the fee is a fallback royalty fee, marks the payer's balance change with a special
     * flag. When the contract service {@code TransferPrecompile} sees this flag, it <i>always</i>
     * requires a payer authorization. (This is different from the normal case with custom fees,
     * where the payer's authorization of the top-level token transfer <i>also</i> authorizes the
     * network to charge the custom fee.)
     *
     * @param payer the payer of the fee
     * @param chargingToken the token charging the fee
     * @param collector the collector of the fee
     * @param denom the denomination of the fee
     * @param amount the amount of the fee
     * @param manager the balance change manager
     * @param isFallbackFee whether the fee is a fallback fee
     */
    static void adjustForAssessed(
            final AccountID payer,
            final TokenID chargingToken,
            final AccountID collector,
            @Nullable final TokenID denom,
            final long amount,
            final TransactionBody.Builder builder,
            final boolean isFallbackFee) {
        final var payerChange = adjustedChange(payer, chargingToken, denom, -amount, builder);
        if (isFallbackFee) {
            payerChange.setIncludesFallbackFee();
        }
        payerChange.setCodeForInsufficientBalance(INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);
        adjustedChange(collector, chargingToken, denom, +amount, builder);
    }

    static TransactionBody.Builder adjustedFractionalChange(
            final AccountID account, final TokenID denom, final long amount, final TransactionBody.Builder builder) {
        return adjustedChange(account, null, denom, amount, builder);
    }

    private static TransactionBody.Builder includedHtsChange(
            final AccountID account, final TokenID denom, final long amount, final TransactionBody.Builder builder) {
        final var newHtsChange = BalanceChange.tokenCustomFeeAdjust(account, denom, amount);
        manager.includeChange(newHtsChange);
        return builder;
    }
}
