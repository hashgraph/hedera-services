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
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeMeta.customFeeMetaFrom;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AssessmentResult;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFixedFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFractionalFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomRoyaltyFeeAssessor;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Charges custom fees for the crypto transfer operation. This is yet to be implemented
 */
public class CustomFeeAssessmentStep {
    private final CryptoTransferTransactionBody op;
    private final CustomFeeAssessor customFeeAssessor;
    private int levelNum = 0;
    private HandleContext context;

    public CustomFeeAssessmentStep(
            @NonNull final CryptoTransferTransactionBody op, final TransferContextImpl transferContext) {
        this.op = op;
        this.context = transferContext.getHandleContext();
        final var fixedFeeAssessor = new CustomFixedFeeAssessor();
        final var fractionalFeeAssessor = new CustomFractionalFeeAssessor(fixedFeeAssessor);
        final var royaltyFeeAssessor = new CustomRoyaltyFeeAssessor(fixedFeeAssessor);
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
        final var hbarTransfers = op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());
        final var tokenStore = handleContext.readableStore(ReadableTokenStore.class);
        final var ledgerConfig = handleContext.configuration().getConfigData(LedgerConfig.class);
        final var tokensConfig = handleContext.configuration().getConfigData(TokensConfig.class);
        final var maxTransfersDepth = ledgerConfig.xferBalanceChangesMaxLen();
        final var maxCustomFeeDepth = tokensConfig.maxCustomFeeDepth();

        // Assess custom fees for given op and produce a next level transaction body builder
        // that needs to be assessed again for custom fees. This is because a custom fee balance
        // change can trigger custom fees again
        final var result = new AssessmentResult(tokenTransfers, hbarTransfers);
        assessCustomFeesFrom(result, tokenTransfers, tokenStore, maxTransfersDepth);

        final var level0Builder = changedInputTxn(op, result);
        if (!result.haveAssessedChanges()) {
            return List.of(level0Builder.build());
        }

        // increment the level to create transaction body from all custom fees assessed from original
        // transaction
        validateTrue(++levelNum <= maxCustomFeeDepth, CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH);
        final var level1Builder = buildBodyFromAdjustments(result);

        // There can only be three levels of custom fees. So assess the generated builder again
        // for last level of custom fees
        final var result2 = new AssessmentResult(
                level1Builder.build().tokenTransfersOrElse(emptyList()),
                level1Builder.build().transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList()));
        result2.setExemptDebits(result.getExemptDebits());
        result2.setRoyaltiesPaid(result.getRoyaltiesPaid());

        assessCustomFeesFrom(result2, level1Builder.build().tokenTransfers(), tokenStore, maxTransfersDepth);

        if (!result2.haveAssessedChanges()) {
            return List.of(level0Builder.build(), level1Builder.build());
        }

        // increment the level to create transaction body from all custom fees assessed from original
        // transaction
        validateTrue(++levelNum <= maxCustomFeeDepth, CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH);
        final var level2Builder = buildBodyFromAdjustments(result2);

        return List.of(level0Builder.build(), level1Builder.build(), level2Builder.build());
    }

    private CryptoTransferTransactionBody.Builder changedInputTxn(
            final CryptoTransferTransactionBody op, final AssessmentResult result) {
        final var copy = op.copyBuilder();
        final var changedTokenTransfers = result.getInputTokenAdjustments();
        final List<AccountAmount> aaList = new ArrayList<>();
        final List<TokenTransferList> tokenTransferLists = new ArrayList<>();

        for (final var entry : changedTokenTransfers.entrySet()) {
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
        copy.tokenTransfers(tokenTransferLists);
        return copy;
    }

    private CryptoTransferTransactionBody.Builder buildBodyFromAdjustments(final AssessmentResult result) {
        final var hbarAdjustments = result.getHbarAdjustments();
        final var tokenAdjustments = result.getHtsAdjustments();

        final var newBuilder = CryptoTransferTransactionBody.newBuilder();
        final var transferList = TransferList.newBuilder();
        final List<AccountAmount> aaList = new ArrayList<>();
        final List<TokenTransferList> tokenTransferLists = new ArrayList<>();

        for (final var entry : hbarAdjustments.entrySet()) {
            aaList.add(AccountAmount.newBuilder()
                    .accountID(entry.getKey())
                    .amount(entry.getValue())
                    .build());
        }
        transferList.accountAmounts(aaList);
        newBuilder.transfers(transferList.build());
        aaList.clear();

        for (final var entry : tokenAdjustments.entrySet()) {
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

    private void assessCustomFeesFrom(
            @NonNull final AssessmentResult result,
            @NonNull final List<TokenTransferList> tokenTransfers,
            @NonNull final ReadableTokenStore tokenStore,
            final int maxTransfersSize) {
        for (final var xfer : tokenTransfers) {
            final var tokenId = xfer.token();
            final var ftTransfers = xfer.transfersOrElse(emptyList());
            final var nftTransfers = xfer.nftTransfersOrElse(emptyList());

            final var token = getIfUsable(tokenId, tokenStore);
            final var feeMeta = customFeeMetaFrom(token);
            if (feeMeta.customFees().isEmpty()) {
                continue;
            }

            for (final var aa : ftTransfers) {
                final var adjustment = aa.amount();
                for (final var fee : feeMeta.customFees()) {
                    final var denom = fee.fixedFeeOrElse(FixedFee.DEFAULT).denominatingTokenIdOrElse(TokenID.DEFAULT);
                    if (couldTriggerCustomFees(tokenId, denom, false, true, adjustment, result.getExemptDebits())) {
                        // If sender for this adjustment is same as treasury for token
                        // then don't charge any custom fee. Since token treasuries are exempt from custom fees
                        if (feeMeta.treasuryId().equals(aa.accountID())) {
                            break;
                        }
                        customFeeAssessor.assess(aa.accountID(), feeMeta, maxTransfersSize, null, result, context);
                    }
                }
            }

            for (final var nftTransfer : nftTransfers) {
                final var adjustment = nftTransfer.serialNumber();
                for (final var fee : feeMeta.customFees()) {
                    final var denomToken =
                            fee.fixedFeeOrElse(FixedFee.DEFAULT).denominatingTokenIdOrElse(TokenID.DEFAULT);
                    if (couldTriggerCustomFees(
                            tokenId, denomToken, true, false, adjustment, result.getExemptDebits())) {
                        // If sender for this adjustment is same as treasury for token
                        // then don't charge any custom fee. Since token treasuries are exempt from custom fees
                        if (feeMeta.treasuryId().equals(nftTransfer.senderAccountID())) {
                            break;
                        }
                        customFeeAssessor.assess(
                                nftTransfer.senderAccountID(),
                                feeMeta,
                                maxTransfersSize,
                                nftTransfer.receiverAccountID(),
                                result,
                                context);
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
    private boolean isExemptFromCustomFees(
            @NonNull final TokenID chargingTokenId,
            @NonNull final TokenID denominatingTokenID,
            @NonNull final Set<TokenID> exemptDebits) {
        /* But self-denominated fees are exempt from further custom fee charging,
        c.f. https://github.com/hashgraph/hedera-services/issues/1925 */

        // Exempt debits will have the denominating token Ids from previous custom fee charging
        // So if the denominating tokenId is in the exempt debits, then it is self-denominated

        return denominatingTokenID != TokenID.DEFAULT && chargingTokenId.equals(denominatingTokenID)
                || exemptDebits.contains(denominatingTokenID);
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
     * @param exemptDebits            the set of tokens that are exempt from custom fee charging, due to being self
     *                                denominated in previous level of custom fee assessment
     * @return true if the adjustment will trigger a custom fee. False otherwise.
     */
    private boolean couldTriggerCustomFees(
            @NonNull final TokenID chargingTokenId,
            @NonNull final TokenID denominatingTokenID,
            final boolean isNftTransfer,
            final boolean isFungibleTokenTransfer,
            final long adjustment,
            @NonNull final Set<TokenID> exemptDebits) {
        if (isExemptFromCustomFees(chargingTokenId, denominatingTokenID, exemptDebits)) {
            return false;
        } else {
            return isNftTransfer || (isFungibleTokenTransfer && adjustment < 0);
        }
    }
}
