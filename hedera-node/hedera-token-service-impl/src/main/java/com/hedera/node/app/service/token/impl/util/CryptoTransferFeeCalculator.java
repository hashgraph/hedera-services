/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.util;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hedera.hapi.node.base.SubType.DEFAULT;
import static com.hedera.hapi.node.base.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hedera.hapi.node.base.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hedera.hapi.node.base.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
import static com.hedera.node.app.hapi.fees.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage.LONG_ACCOUNT_AMOUNT_BYTES;
import static com.hedera.node.app.hapi.fees.usage.token.TokenOpsUsage.LONG_BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.fees.usage.token.entities.TokenEntitySizes.TOKEN_ENTITY_SIZES;

import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.node.app.service.token.impl.handlers.transfer.CustomFeeAssessmentStep;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.FeesConfig;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class CryptoTransferFeeCalculator {

    private CryptoTransferFeeCalculator() {
        // Util class
    }

    public static Fees calculate(
            FeeContext feeContext, @Nullable TransferList transferList, List<TokenTransferList> tokenTransferList) {

        final var config = feeContext.configuration();
        final var tokenMultiplier = config.getConfigData(FeesConfig.class).tokenTransferUsageMultiplier();

        /* BPT calculations shouldn't include any custom fee payment usage */
        int totalXfers = transferList != null ? transferList.accountAmounts().size() : 0;

        var totalTokensInvolved = 0;
        var totalTokenTransfers = 0;
        var numNftOwnershipChanges = 0;
        for (final var tokenTransfers : tokenTransferList) {
            totalTokensInvolved++;
            totalTokenTransfers += tokenTransfers.transfers().size();
            numNftOwnershipChanges += tokenTransfers.nftTransfers().size();
        }

        int weightedTokensInvolved = tokenMultiplier * totalTokensInvolved;
        int weightedTokenXfers = tokenMultiplier * totalTokenTransfers;
        final var bpt = weightedTokensInvolved * LONG_BASIC_ENTITY_ID_SIZE
                + (weightedTokenXfers + totalXfers) * LONG_ACCOUNT_AMOUNT_BYTES
                + TOKEN_ENTITY_SIZES.bytesUsedForUniqueTokenTransfers(numNftOwnershipChanges);

        /* Include custom fee payment usage in RBS calculations */
        var customFeeHbarTransfers = 0;
        var customFeeTokenTransfers = 0;
        final var involvedTokens = new HashSet<TokenID>();
        final var customFeeAssessor = new CustomFeeAssessmentStep(CryptoTransferTransactionBody.newBuilder()
                .transfers(transferList)
                .tokenTransfers(tokenTransferList)
                .build());
        List<AssessedCustomFee> assessedCustomFees;
        boolean triedAndFailedToUseCustomFees = false;
        try {
            assessedCustomFees = customFeeAssessor.assessNumberOfCustomFees(feeContext);
        } catch (HandleException ignore) {
            final var status = ignore.getStatus();
            // If the transaction tried and failed to use custom fees, enable this flag.
            // This is used to charge a different canonical fees.
            triedAndFailedToUseCustomFees = status == INSUFFICIENT_PAYER_BALANCE_FOR_CUSTOM_FEE
                    || status == INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE
                    || status == CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
            assessedCustomFees = new ArrayList<>();
        }
        for (final var fee : assessedCustomFees) {
            if (!fee.hasTokenId()) {
                customFeeHbarTransfers++;
            } else {
                customFeeTokenTransfers++;
                involvedTokens.add(fee.tokenId());
            }
        }
        totalXfers += customFeeHbarTransfers;
        weightedTokenXfers += tokenMultiplier * customFeeTokenTransfers;
        weightedTokensInvolved += tokenMultiplier * involvedTokens.size();
        long rbs = (totalXfers * LONG_ACCOUNT_AMOUNT_BYTES)
                + TOKEN_ENTITY_SIZES.bytesUsedToRecordTokenTransfers(
                        weightedTokensInvolved, weightedTokenXfers, numNftOwnershipChanges);

        /* Get subType based on the above information */
        final var subType = getSubType(
                numNftOwnershipChanges,
                totalTokenTransfers,
                customFeeHbarTransfers,
                customFeeTokenTransfers,
                triedAndFailedToUseCustomFees);
        return feeContext
                .feeCalculatorFactory()
                .feeCalculator(subType)
                .addBytesPerTransaction(bpt)
                .addRamByteSeconds(rbs * USAGE_PROPERTIES.legacyReceiptStorageSecs())
                .calculate();
    }

    /**
     * Get the subType based on the number of NFT ownership changes, number of fungible token transfers,
     * number of custom fee hbar transfers, number of custom fee token transfers and whether the transaction
     * tried and failed to use custom fees.
     * @param numNftOwnershipChanges number of NFT ownership changes
     * @param numFungibleTokenTransfers number of fungible token transfers
     * @param customFeeHbarTransfers number of custom fee hbar transfers
     * @param customFeeTokenTransfers number of custom fee token transfers
     * @param triedAndFailedToUseCustomFees whether the transaction tried and failed while validating custom fees.
     *                                      If the failure includes custom fee error codes, the fee charged should not
     *                                      use SubType.DEFAULT.
     * @return the subType
     */
    private static SubType getSubType(
            final int numNftOwnershipChanges,
            final int numFungibleTokenTransfers,
            final int customFeeHbarTransfers,
            final int customFeeTokenTransfers,
            final boolean triedAndFailedToUseCustomFees) {
        if (triedAndFailedToUseCustomFees) {
            return TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
        }
        if (numNftOwnershipChanges != 0) {
            if (customFeeHbarTransfers > 0 || customFeeTokenTransfers > 0) {
                return TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
            }
            return TOKEN_NON_FUNGIBLE_UNIQUE;
        }
        if (numFungibleTokenTransfers != 0) {
            if (customFeeHbarTransfers > 0 || customFeeTokenTransfers > 0) {
                return TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
            }
            return TOKEN_FUNGIBLE_COMMON;
        }
        return DEFAULT;
    }
}
