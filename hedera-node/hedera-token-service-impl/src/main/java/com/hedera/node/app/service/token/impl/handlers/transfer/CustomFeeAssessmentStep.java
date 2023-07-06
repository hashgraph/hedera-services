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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeMeta;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFixedFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFractionalFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomRoyaltyFeeAssessor;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Charges custom fees for the crypto transfer operation. This is yet to be implemented
 */
public class CustomFeeAssessmentStep {
    private final CryptoTransferTransactionBody op;
    private final CustomFeeAssessor customFeeAssessor;
    private final CustomFixedFeeAssessor fixedFeeAssessor;
    private final CustomFractionalFeeAssessor fractionalFeeAssessor;
    private final CustomRoyaltyFeeAssessor royaltyFeeAssessor;

    public CustomFeeAssessmentStep(@NonNull final CryptoTransferTransactionBody op) {
        this.op = op;
        fixedFeeAssessor = new CustomFixedFeeAssessor();
        fractionalFeeAssessor = new CustomFractionalFeeAssessor();
        royaltyFeeAssessor = new CustomRoyaltyFeeAssessor();
        customFeeAssessor = new CustomFeeAssessor(fixedFeeAssessor, fractionalFeeAssessor, royaltyFeeAssessor, op);
    }

    public CryptoTransferTransactionBody.Builder assessCustomFees(@NonNull final TransferContext transferContext) {
        requireNonNull(transferContext);
        final var customFeeAssessments = new ArrayList<>();

        final var handleContext = transferContext.getHandleContext();
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        final var tokenStore = handleContext.readableStore(ReadableTokenStore.class);
        final var ledgerConfig = handleContext.configuration().getConfigData(LedgerConfig.class);
        final var tokensConfig = handleContext.configuration().getConfigData(TokensConfig.class);

        final var nextLevelBuilder = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder().build())
                .tokenTransfers(TokenTransferList.newBuilder()
                        .transfers(emptyList())
                        .nftTransfers(emptyList())
                        .build());

        final var hbarAdjustments = TransferList.newBuilder();
        final List<TokenTransferList.Builder> htsAdjustments = new ArrayList<>();

        for (final var xfer : tokenTransfers) {
            final var tokenId = xfer.token();
            final var fungibleTokenTransfers = xfer.transfersOrElse(emptyList());
            final var nftTransfers = xfer.nftTransfersOrElse(emptyList());

            final var token = getIfUsable(tokenId, tokenStore);
            final var feeMeta =
                    new CustomFeeMeta(tokenId, token.treasuryAccountId(), token.customFeesOrElse(emptyList()));
            if (feeMeta.customFees().isEmpty()) {
                continue;
            }

            for (final var aa : fungibleTokenTransfers) {
                final var adjustment = aa.amount();
                for (final var fee : feeMeta.customFees()) {
                    final var denomToken = fee.fixedFee().denominatingTokenId();
                    if (couldTriggerCustomFees(tokenId, denomToken, false, true, adjustment)) {
                        customFeeAssessor.assess(
                                aa.accountID(), feeMeta, tokensConfig, ledgerConfig, hbarAdjustments, htsAdjustments);
                    }
                }
            }

            for (final var nftTransfer : nftTransfers) {
                final var adjustment = nftTransfer.serialNumber();
                for (final var fee : feeMeta.customFees()) {
                    final var denomToken = fee.fixedFee().denominatingTokenId();
                    if (couldTriggerCustomFees(tokenId, denomToken, true, false, adjustment)) {
                        customFeeAssessor.assess(
                                nftTransfer.senderAccountID(),
                                feeMeta,
                                tokensConfig,
                                ledgerConfig,
                                hbarAdjustments,
                                htsAdjustments);
                    }
                }
            }
        }
        nextLevelBuilder.tokenTransfers(tokenTransfers).transfers(hbarAdjustments.build());
        customFeeAssessments.add(nextLevelBuilder);
        return nextLevelBuilder;
    }

    /**
     * Checks if the adjustment will trigger a custom fee.
     * Custom fee is triggered if the fee is not self-denominated and the transfer is not a hbar transfer
     * and the adjustment is a debit.
     * @param chargingTokenId the token that is being charged
     * @param denominatingTokenID the token that is being used as denomination to pay the fee
     * @param isNftTransfer true if the transfer is an NFT transfer
     * @param isFungibleTokenTransfer true if the transfer is a fungible token transfer
     * @param adjustment the amount of the transfer
     * @return true if the adjustment will trigger a custom fee. False otherwise.
     */
    private boolean couldTriggerCustomFees(
            @NonNull TokenID chargingTokenId,
            @NonNull TokenID denominatingTokenID,
            boolean isNftTransfer,
            boolean isFungibleTokenTransfer,
            long adjustment) {
        if (isExemptFromCustomFees(chargingTokenId, denominatingTokenID)) {
            return false;
        } else {
            return isNftTransfer || (isFungibleTokenTransfer && adjustment < 0);
        }
    }

    /**
     * Custom fee that is self-denominated is exempt from further custom fee charging.
     * @param chargingTokenId the token that is being charged
     * @param denominatingTokenID the token that is being used as denomination to pay the fee
     * @return true if the custom fee is self-denominated
     */
    private boolean isExemptFromCustomFees(TokenID chargingTokenId, TokenID denominatingTokenID) {
        /* But self-denominated fees are exempt from further custom fee charging,
        c.f. https://github.com/hashgraph/hedera-services/issues/1925 */
        return chargingTokenId.equals(denominatingTokenID);
    }
}
