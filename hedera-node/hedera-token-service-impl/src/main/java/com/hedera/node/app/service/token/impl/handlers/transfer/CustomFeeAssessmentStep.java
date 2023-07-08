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

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeAssessmentResult;
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
    private int levelNum = 0;

    public CustomFeeAssessmentStep(@NonNull final CryptoTransferTransactionBody op) {
        this.op = op;
        fixedFeeAssessor = new CustomFixedFeeAssessor();
        fractionalFeeAssessor = new CustomFractionalFeeAssessor();
        royaltyFeeAssessor = new CustomRoyaltyFeeAssessor();
        customFeeAssessor = new CustomFeeAssessor(fixedFeeAssessor, fractionalFeeAssessor, royaltyFeeAssessor, op);
    }

    /**
     * Given a transaction body, assess custom fees for the crypto transfer operation.
     * It iterates through the token transfer list and assesses custom fees for each token transfer.
     * It creates 2 new hashmaps with assessed hbar fees and assessed token fees.
     * It is possible the assessed token custom fees could trigger custom fee again.
     * So, we repeat the process for the assessed custom fees one more time.
     *
     * @param transferContext - transfer context
     * @return - transaction body with assessed custom fees
     */
    public List<CryptoTransferTransactionBody> assessCustomFees(@NonNull final TransferContext transferContext) {
        requireNonNull(transferContext);

        final var handleContext = transferContext.getHandleContext();
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        final var tokenStore = handleContext.readableStore(ReadableTokenStore.class);
        final var ledgerConfig = handleContext.configuration().getConfigData(LedgerConfig.class);
        final var tokensConfig = handleContext.configuration().getConfigData(TokensConfig.class);
        final var maxTransfersDepth = ledgerConfig.xferBalanceChangesMaxLen();

        // Assess custom fees for given op and produce a next level transaction body builder
        // that needs to be assessed again for custom fees. This is because a custom fee balance
        // change can trigger custom fees again
        final var resultLevel1 = assessCustomFeesForALevel(tokenTransfers, tokenStore, maxTransfersDepth);

        // If there are no custom fees charged for the given transaction, return the original transaction
        if (allResultsEmpty(resultLevel1)) {
            return List.of(op);
        }
        // increment the level to create transaction body from all custom fees assessed from original
        // transaction
        validateTrue(++levelNum <= tokensConfig.maxCustomFeeDepth(), CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH);

        final var level2Builder =
                buildTransactionFromAdjustments(resultLevel1.newHbarAdjustments(), resultLevel1.newHtsAdjustments());

        // There can only be three levels of custom fees. So assess the generated builder again
        // for last level of custom fees
        final var resultLevel2 =
                assessCustomFeesForALevel(level2Builder.build().tokenTransfers(), tokenStore, maxTransfersDepth);
        // increment the level to create transaction body from all custom fees assessed from original
        // transaction
        levelNum++;
        validateTrue(levelNum <= tokensConfig.maxCustomFeeDepth(), CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH);

        final var level3Builder =
                buildTransactionFromAdjustments(resultLevel2.newHbarAdjustments(), resultLevel2.newHtsAdjustments());
        return List.of(level2Builder.build(), level3Builder.build());
    }

    private boolean allResultsEmpty(final CustomFeeAssessmentResult resultLevel1) {
        return resultLevel1.newHbarAdjustments().isEmpty()
                && resultLevel1.newHtsAdjustments().isEmpty()
                && resultLevel1.inputTxnAdjustments().isEmpty();
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

    private CustomFeeAssessmentResult assessCustomFeesForALevel(
            List<TokenTransferList> tokenTransfers, ReadableTokenStore tokenStore, final int maxTransfersSize) {
        final Map<TokenID, Map<AccountID, Long>> newCustomFeeTokenAdjustments = new HashMap<>();
        // two maps to aggregate all custom fee balance changes. These two maps are used
        // to construct a transaction body that needs to be assessed again for custom fees
        final Map<AccountID, Long> newCustomFeeHbarAdjustments = new HashMap<>();
        // Any debits in this set should not trigger custom fee charging again
        final Set<TokenID> exemptDebits = new HashSet<>();
        final Map<TokenID, Map<AccountID, Long>> inputTokenTransfers = buildTokenTransferMap(tokenTransfers);

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
                                inputTokenTransfers,
                                newCustomFeeHbarAdjustments,
                                newCustomFeeTokenAdjustments,
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
                                inputTokenTransfers,
                                newCustomFeeHbarAdjustments,
                                newCustomFeeTokenAdjustments,
                                exemptDebits,
                                maxTransfersSize);
                    }
                }
            }
        }
        return new CustomFeeAssessmentResult(
                newCustomFeeHbarAdjustments, newCustomFeeTokenAdjustments, inputTokenTransfers, exemptDebits);
    }

    private Map<TokenID, Map<AccountID, Long>> buildTokenTransferMap(final List<TokenTransferList> tokenTransfers) {
        final var fungibleTransfersMap = new HashMap<TokenID, Map<AccountID, Long>>();
        for (final var xfer : tokenTransfers) {
            final var tokenId = xfer.token();
            final var fungibleTokenTransfers = xfer.transfersOrElse(emptyList());
            final var tokenTransferMap = new HashMap<AccountID, Long>();
            for (final var aa : fungibleTokenTransfers) {
                tokenTransferMap.put(aa.accountID(), aa.amount());
            }
            fungibleTransfersMap.put(tokenId, tokenTransferMap);
        }
        return fungibleTransfersMap;
    }

    /**
     * Custom fee that is self-denominated is exempt from further custom fee charging.
     *
     * @param chargingTokenId     the token that is being charged
     * @param denominatingTokenID the token that is being used as denomination to pay the fee
     * @param exemptDebits
     * @return true if the custom fee is self-denominated
     */
    private boolean isExemptFromCustomFees(
            TokenID chargingTokenId, TokenID denominatingTokenID, final Set<TokenID> exemptDebits) {
        /* But self-denominated fees are exempt from further custom fee charging,
        c.f. https://github.com/hashgraph/hedera-services/issues/1925 */
        return chargingTokenId.equals(denominatingTokenID) || exemptDebits.contains(denominatingTokenID);
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
