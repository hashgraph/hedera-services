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

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
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

import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

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
        customFeeAssessor = new CustomFeeAssessor(fixedFeeAssessor, fractionalFeeAssessor, royaltyFeeAssessor);
    }
    public List<TransactionBody.Builder> assessCustomFees(@NonNull final TransferContext transferContext) {
        requireNonNull(transferContext);
        final var customFeeAssessments = new ArrayList<>();

        final var handleContext = transferContext.getHandleContext();
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        final var nftStore = handleContext.readableStore(ReadableNftStore.class);
        final var accountStore = handleContext.readableStore(ReadableAccountStore.class);
        final var tokenStore = handleContext.readableStore(ReadableTokenStore.class);
        final var tokenRelStore = handleContext.readableStore(ReadableTokenRelationStore.class);
        final var expiryValidator = handleContext.expiryValidator();
        final var ledgerConfig = handleContext.configuration().getConfigData(LedgerConfig.class);
        final var tokensConfig = handleContext.configuration().getConfigData(TokensConfig.class);
        final var maxXferBalanceChanges = ledgerConfig.xferBalanceChangesMaxLen();
        final var maxNestedCustomFees = tokensConfig.maxCustomFeeDepth();
        final var maxCustomFeesAllowed = tokensConfig.maxCustomFeesAllowed();
        final var levelOneBody = op.copyBuilder();
        final var levelTwoBody = TransactionBody.newBuilder();

        for (final var xfer : tokenTransfers) {
            final var tokenId = xfer.token();
            final var fungibleTokenTransfers = xfer.transfersOrElse(emptyList());
            final var nftTransfers = xfer.nftTransfersOrElse(emptyList());

            final var token = getIfUsable(tokenId, tokenStore);
            final var feeMeta = new CustomFeeMeta(tokenId, token.treasuryAccountId(), token.customFeesOrElse(emptyList()));
            if (feeMeta.customFees().isEmpty()) {
                continue;
            }
            for(final var aa : fungibleTokenTransfers) {
                final var adjustment = aa.amount();
                for(final var fee : feeMeta.customFees()) {
                    final var denomToken = fee.fixedFee().denominatingTokenId();
                    if(couldTriggerCustomFees(tokenId,denomToken, false, true, adjustment)){
                        customFeeAssessor.assess();
                    }
                }
            }

            for(final var nftTransfer : nftTransfers){

            }

        }
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
    private boolean couldTriggerCustomFees(@NonNull TokenID chargingTokenId,
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
    private boolean isExemptFromCustomFees(TokenID chargingTokenId, TokenID denominatingTokenID){
        /* But self-denominated fees are exempt from further custom fee charging,
            c.f. https://github.com/hashgraph/hedera-services/issues/1925 */
        return chargingTokenId.equals(denominatingTokenID);
    }
}
