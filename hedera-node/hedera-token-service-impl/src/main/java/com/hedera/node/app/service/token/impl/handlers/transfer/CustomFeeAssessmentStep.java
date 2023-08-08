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

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
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
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AssessmentResult;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFixedFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFractionalFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomRoyaltyFeeAssessor;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Charges custom fees for the crypto transfer operation.
 * Custom fees can be a Fixed Fee (HBAR or HTS), Fractional Fee or Royalty Fee.
 * Any fixed HTS fees that are not self-denominated can trigger next level of custom fees assessment.
 * Fractional fees and Royalty fees are not recursive.
 * When assessing custom fees in this approach, we build list of transaction bodies that include assessed custom fees.
 * We also build list of assessed custom fees to be added to the record.
 * We do this in 2 steps:
 * 1. Assess custom fees for the transaction body given as input (Level-0 Body)
 * 2. If there are any fractional fees, adjust the assessed changes in the Level-0 Body
 * 3. Any non-self denominated fixed (HBAR or HTS) fees, assess them and create Level-1 Body.
 * But any self denominated fees will be adjusted in Level- 0 Body (since they can't trigger further custom fee charging.)
 * 4.Any royalty fees which are not self denominated will be added to level-1 body.
 */
public class CustomFeeAssessmentStep {
    private final CryptoTransferTransactionBody op;
    private final CustomFeeAssessor customFeeAssessor;
    private int levelNum = 0;
    private final HandleContext context;
    private int totalBalanceChanges = 0;
    private static final int MAX_PLAUSIBLE_LEVEL_NUM = 10;
    private static final Logger log = LogManager.getLogger(CustomFeeAssessmentStep.class);

    public CustomFeeAssessmentStep(
            @NonNull final CryptoTransferTransactionBody op, final TransferContextImpl transferContext) {
        this.op = op;
        this.context = transferContext.getHandleContext();
        final var fixedFeeAssessor = new CustomFixedFeeAssessor();
        final var fractionalFeeAssessor = new CustomFractionalFeeAssessor(fixedFeeAssessor);
        final var royaltyFeeAssessor = new CustomRoyaltyFeeAssessor(fixedFeeAssessor);
        customFeeAssessor = new CustomFeeAssessor(fixedFeeAssessor, fractionalFeeAssessor, royaltyFeeAssessor);
        customFeeAssessor.calculateAndSetInitialNftChanges(op);
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
        final var tokenStore = handleContext.readableStore(ReadableTokenStore.class);
        final var ledgerConfig = handleContext.configuration().getConfigData(LedgerConfig.class);
        final var tokensConfig = handleContext.configuration().getConfigData(TokensConfig.class);
        final var maxTransfersAllowed = ledgerConfig.xferBalanceChangesMaxLen();
        final var maxCustomFeeDepth = tokensConfig.maxCustomFeeDepth();
        final var recordBuilder = handleContext.recordBuilder(CryptoTransferRecordBuilder.class);
        // list of total assessed custom fees to be added to the record
        final List<AssessedCustomFee> customFeesAssessed = new ArrayList<>();
        // the transaction to be assessed
        var txnToAssess = op;
        // list of assessed transactions, to be fed into further steps
        final List<CryptoTransferTransactionBody> assessedTxns = new ArrayList<>();

        // The first assessment inputs
        var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        var hbarTransfers = op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());

        do {
            validateTrue(levelNum <= maxCustomFeeDepth, CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH);
            // The result after each assessment
            final var result = assessCustomFeesFrom(hbarTransfers, tokenTransfers, tokenStore, maxTransfersAllowed);

            // when there are adjustments made to given transaction, need to re-build the transaction
            final var modifiedInputBody = changedInputTxn(txnToAssess, result);
            assessedTxns.add(modifiedInputBody);

            validateTotalAdjustments(modifiedInputBody, maxTransfersAllowed);
            customFeesAssessed.addAll(result.getAssessedCustomFees());

            // build body from assessed custom fees to be fed to next level of assessment
            txnToAssess = buildBodyFromAdjustments(result);

            tokenTransfers = txnToAssess.tokenTransfersOrElse(emptyList());
            hbarTransfers = txnToAssess.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());

            levelNum++;
        } while (!tokenTransfers.isEmpty() && levelNum <= MAX_PLAUSIBLE_LEVEL_NUM);

        if (levelNum > MAX_PLAUSIBLE_LEVEL_NUM) {
            log.error("Recursive charging exceeded maximum plausible depth for transaction {}", op);
            throw new IllegalStateException("Custom fee charging exceeded max recursion depth");
        }

        if (!hbarTransfers.isEmpty()) {
            assessedTxns.add(txnToAssess);
        }

        recordBuilder.assessedCustomFees(customFeesAssessed);
        customFeeAssessor.resetInitialNftChanges();
        return assessedTxns;
    }

    private void validateTotalAdjustments(final CryptoTransferTransactionBody op, final int maxTransfersDepth) {
        final var hbarTransfers = op.transfersOrElse(TransferList.DEFAULT)
                .accountAmountsOrElse(emptyList())
                .size();
        var fungibleTokenChanges = 0;
        var nftTransfers = 0;
        for (final var xfer : op.tokenTransfersOrElse(emptyList())) {
            fungibleTokenChanges += xfer.transfersOrElse(emptyList()).size();
            nftTransfers += xfer.nftTransfersOrElse(emptyList()).size();
        }

        totalBalanceChanges += hbarTransfers + fungibleTokenChanges + nftTransfers;
        // totalBalanceChanges should be less than maxTransfersDepth
        validateTrue(totalBalanceChanges <= maxTransfersDepth, CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS);
    }

    private CryptoTransferTransactionBody changedInputTxn(
            final CryptoTransferTransactionBody op, final AssessmentResult result) {
        final var copy = op.copyBuilder();
        final var changedFungibleTokenTransfers = result.getMutableInputTokenAdjustments();
        final List<TokenTransferList> tokenTransferLists = new ArrayList<>();
        // If there are no changes for the token , add as it is
        for (final var xfers : op.tokenTransfers()) {
            final var token = xfers.token();
            if (!changedFungibleTokenTransfers.containsKey(token)) {
                tokenTransferLists.add(xfers);
            }
        }
        // If there are changes modify the token transfer list
        for (final var entry : changedFungibleTokenTransfers.entrySet()) {
            final var tokenTransferList = TokenTransferList.newBuilder().token(entry.getKey());
            final var aaList = new ArrayList<AccountAmount>();
            for (final var valueEntry : entry.getValue().entrySet()) {
                aaList.add(AccountAmount.newBuilder()
                        .accountID(valueEntry.getKey())
                        .amount(valueEntry.getValue())
                        .build());
            }
            tokenTransferList.transfers(aaList);
            final var expectedDecimals = getExpectedDecimalsFor(op.tokenTransfers(), entry.getKey());
            if (expectedDecimals != null) {
                tokenTransferList.expectedDecimals(expectedDecimals);
            }
            tokenTransferLists.add(tokenTransferList.build());
        }
        copy.tokenTransfers(tokenTransferLists);
        return copy.build();
    }

    private Integer getExpectedDecimalsFor(final List<TokenTransferList> tokenTransferLists, final TokenID key) {
        for (final var tokenTransferList : tokenTransferLists) {
            if (tokenTransferList.token().equals(key)) {
                return tokenTransferList.expectedDecimals();
            }
        }
        return null;
    }

    private CryptoTransferTransactionBody buildBodyFromAdjustments(final AssessmentResult result) {
        final var hbarAdjustments = result.getHbarAdjustments();
        final var tokenAdjustments = result.getHtsAdjustments();

        final var newBuilder = CryptoTransferTransactionBody.newBuilder();
        final var transferList = TransferList.newBuilder();
        final List<AccountAmount> hbarList = new ArrayList<>();
        final List<TokenTransferList> tokenTransferLists = new ArrayList<>();

        for (final var entry : hbarAdjustments.entrySet()) {
            hbarList.add(AccountAmount.newBuilder()
                    .accountID(entry.getKey())
                    .amount(entry.getValue())
                    .build());
        }
        transferList.accountAmounts(hbarList);
        newBuilder.transfers(transferList.build());

        for (final var entry : tokenAdjustments.entrySet()) {
            final var tokenTransferList = TokenTransferList.newBuilder().token(entry.getKey());
            final List<AccountAmount> aaList = new ArrayList<>();
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
        return newBuilder.build();
    }

    private AssessmentResult assessCustomFeesFrom(
            @NonNull final List<AccountAmount> hbarTransfers,
            @NonNull final List<TokenTransferList> tokenTransfers,
            @NonNull final ReadableTokenStore tokenStore,
            final int maxTransfersSize) {
        final var result = new AssessmentResult(tokenTransfers, hbarTransfers);

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
                if (adjustment < 0) {
                    final var sender = aa.accountID();
                    // If sender for this adjustment is same as treasury for token
                    // then don't charge any custom fee. Since token treasuries are exempt from custom fees
                    if (feeMeta.treasuryId().equals(sender)) {
                        continue;
                    }
                    customFeeAssessor.assess(sender, feeMeta, maxTransfersSize, null, result, context);
                }
            }

            for (final var nftTransfer : nftTransfers) {
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
        return result;
    }
}
