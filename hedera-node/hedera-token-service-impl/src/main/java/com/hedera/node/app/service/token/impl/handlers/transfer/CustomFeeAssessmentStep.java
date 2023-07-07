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

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public CryptoTransferTransactionBody assessCustomFees(@NonNull final TransferContext transferContext) {
        requireNonNull(transferContext);

        final var handleContext = transferContext.getHandleContext();
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        final var tokenStore = handleContext.readableStore(ReadableTokenStore.class);
        final var ledgerConfig = handleContext.configuration().getConfigData(LedgerConfig.class);
        final var tokensConfig = handleContext.configuration().getConfigData(TokensConfig.class);

        // two maps to aggregate all custom fee balance changes. These two maps are used
        // to construct a transaction body that needs to be assessed again for custom fees
        final Map<TokenID, Map<AccountID, Long>> customFeeTokenAdjustmentsLevel1 = new HashMap<>();
        final Map<AccountID, Long> customFeeHbarAdjustmentsLevel1 = new HashMap<>();
        // Any debits in this set should not trigger custom fee charging again
        final Set<TokenID> exemptDebitsLevel1 = new HashSet<>();

        // Assess custom fees for given op and produce a next level transaction body builder
        // that needs to be assessed again for custom fees. This is because a custom fee balance
        // change can trigger custom fees again
        assessCustomFeesForALevel(
                tokenTransfers,
                tokenStore,
                tokensConfig,
                customFeeHbarAdjustmentsLevel1,
                customFeeTokenAdjustmentsLevel1,
                exemptDebitsLevel1);

        final var level2Builder =
                buildTransactionFromAdjustments(customFeeHbarAdjustmentsLevel1, customFeeTokenAdjustmentsLevel1);

        // There can only be three levels of custom fees. So assess the generated builder again
        // for last level of custom fees
        assessCustomFeesForALevel(
                level2Builder.build().tokenTransfers(),
                tokenStore,
                tokensConfig,
                customFeeHbarAdjustmentsLevel1,
                customFeeTokenAdjustmentsLevel1,
                exemptDebitsLevel1);

        final var level3Builder =
                buildTransactionFromAdjustments(customFeeHbarAdjustmentsLevel1, customFeeTokenAdjustmentsLevel1);
        return level3Builder.build();
    }

    private CryptoTransferTransactionBody.Builder buildTransactionFromAdjustments(
            final Map<AccountID, Long> customFeeHbarAdjustments,
            final Map<TokenID, Map<AccountID, Long>> customFeeTokenAdjustments) {
        final var newBuilder = CryptoTransferTransactionBody.newBuilder();
        final var transferList = TransferList.newBuilder();
        final List<AccountAmount> aaList = new ArrayList<>();
        final List<TokenTransferList> tokenTransferLists = new ArrayList<>();

        for (final var entry : customFeeHbarAdjustments.entrySet()) {
            aaList.add(AccountAmount.newBuilder()
                    .accountID(entry.getKey())
                    .amount(entry.getValue())
                    .build());
        }
        transferList.accountAmounts(aaList);
        newBuilder.transfers(transferList.build());
        aaList.clear();

        for (final var entry : customFeeTokenAdjustments.entrySet()) {
            final var tokenTransferList = TokenTransferList.newBuilder().token(entry.getKey());
            for (final var valueEntry : entry.getValue().entrySet()) {
                aaList.add(AccountAmount.newBuilder()
                        .accountID(valueEntry.getKey())
                        .amount(valueEntry.getValue())
                        .build());
            }
            tokenTransferList.transfers(aaList);
            tokenTransferLists.add(tokenTransferList.build());
        }
        newBuilder.tokenTransfers(tokenTransferLists);
        return newBuilder;
    }

    private void assessCustomFeesForALevel(
            List<TokenTransferList> tokenTransfers,
            ReadableTokenStore tokenStore,
            TokensConfig tokensConfig,
            LedgerConfig ledgerConfig,
            Map<AccountID, Long> customFeeHbarAdjustments,
            Map<TokenID, Map<AccountID, Long>> customFeeTokenAdjustments,
            Set<TokenID> exemptDebits) {
        final int maxTransfersSize = ledgerConfig.xferBalanceChangesMaxLen();
        for (final var xfer : tokenTransfers) {
            final var tokenId = xfer.token();
            final var fungibleTokenTransfers = xfer.transfersOrElse(emptyList());
            final var nftTransfers = xfer.nftTransfersOrElse(emptyList());

            final var token = getIfUsable(tokenId, tokenStore);
            final var feeMeta = new CustomFeeMeta(
                    tokenId, token.treasuryAccountId(), token.customFeesOrElse(emptyList()), token.tokenType());
            if (feeMeta.customFees().isEmpty()) {
                continue;
            }

            for (final var aa : fungibleTokenTransfers) {
                final var adjustment = aa.amount();
                for (final var fee : feeMeta.customFees()) {
                    final var denomToken = fee.fixedFee().denominatingTokenId();
                    if (couldTriggerCustomFees(tokenId, denomToken, false, true, adjustment, exemptDebits)) {
                        customFeeAssessor.assess(
                                aa.accountID(),
                                feeMeta,
                                tokensConfig,
                                customFeeHbarAdjustments,
                                customFeeTokenAdjustments,
                                exemptDebits,
                                maxTransfersSize);
                    }
                }
            }

            for (final var nftTransfer : nftTransfers) {
                final var adjustment = nftTransfer.serialNumber();
                for (final var fee : feeMeta.customFees()) {
                    final var denomToken = fee.fixedFee().denominatingTokenId();
                    if (couldTriggerCustomFees(tokenId, denomToken, true, false, adjustment, exemptDebits)) {
                        customFeeAssessor.assess(
                                nftTransfer.senderAccountID(),
                                feeMeta,
                                tokensConfig,
                                customFeeHbarAdjustments,
                                customFeeTokenAdjustments,
                                exemptDebits,
                                maxTransfersSize);
                    }
                }
            }
        }
    }

    /**
     * Custom fee that is self-denominated is exempt from further custom fee charging.
     *
     * @param chargingTokenId     the token that is being charged
     * @param denominatingTokenID the token that is being used as denomination to pay the fee
     * @param exemptDebits
     * @return true if the custom fee is self-denominated
     */
    private boolean isExemptFromCustomFees(TokenID chargingTokenId,
                                           TokenID denominatingTokenID,
                                           final Set<TokenID> exemptDebits) {
        /* But self-denominated fees are exempt from further custom fee charging,
        c.f. https://github.com/hashgraph/hedera-services/issues/1925 */
        return chargingTokenId.equals(denominatingTokenID)|| exemptDebits.contains(denominatingTokenID);
    }
    /**
     * Checks if the adjustment will trigger a custom fee.
     * Custom fee is triggered if the fee is not self-denominated and the transfer is not a hbar transfer
     * and the adjustment is a debit.
     *
     * @param chargingTokenId         the token that is being charged
     * @param denominatingTokenID     the token that is being used as denomination to pay the fee
     * @param isNftTransfer           true if the transfer is an NFT transfer
     * @param isFungibleTokenTransfer true if the transfer is a fungible token transfer
     * @param adjustment              the amount of the transfer
     * @param exemptDebits
     * @return true if the adjustment will trigger a custom fee. False otherwise.
     */
    private boolean couldTriggerCustomFees(
            @NonNull TokenID chargingTokenId,
            @NonNull TokenID denominatingTokenID,
            boolean isNftTransfer,
            boolean isFungibleTokenTransfer,
            long adjustment,
            final Set<TokenID> exemptDebits) {
        if (isExemptFromCustomFees(chargingTokenId, denominatingTokenID, exemptDebits)) {
            return false;
        } else {
            return isNftTransfer || (isFungibleTokenTransfer && adjustment < 0);
        }
    }
}
