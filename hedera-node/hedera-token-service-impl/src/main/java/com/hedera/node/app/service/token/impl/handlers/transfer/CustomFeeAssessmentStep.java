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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeMeta.customFeeMetaFrom;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AssessmentResult;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFixedFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFractionalFeeAssessor;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomRoyaltyFeeAssessor;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
    private int totalBalanceChanges = 0;
    private static final int MAX_PLAUSIBLE_LEVEL_NUM = 10;
    private static final Logger log = LogManager.getLogger(CustomFeeAssessmentStep.class);

    public CustomFeeAssessmentStep(@NonNull final CryptoTransferTransactionBody op) {
        this.op = op;
        final var fixedFeeAssessor = new CustomFixedFeeAssessor();
        final var fractionalFeeAssessor = new CustomFractionalFeeAssessor(fixedFeeAssessor);
        final var royaltyFeeAssessor = new CustomRoyaltyFeeAssessor(fixedFeeAssessor);
        customFeeAssessor = new CustomFeeAssessor(fixedFeeAssessor, fractionalFeeAssessor, royaltyFeeAssessor);
        customFeeAssessor.calculateAndSetInitialNftChanges(op);
    }

    /**
     * Given a transaction body, assess custom fees for the crypto transfer operation.
     * This is called in Ingest and handle to fetch fees for CryptoTransfer transaction.
     *
     * @param feeContext - fee context
     * @return - list of assessed custom fees
     */
    public List<AssessedCustomFee> assessNumberOfCustomFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);

        final var tokenStore = feeContext.readableStore(ReadableTokenStore.class);
        final var tokenRelStore = feeContext.readableStore(ReadableTokenRelationStore.class);
        final var readableStore = feeContext.readableStore(ReadableAccountStore.class);
        final var config = feeContext.configuration();
        final var result = assessFees(tokenStore, tokenRelStore, config, readableStore, AccountID::hasAlias);
        return result.assessedCustomFees();
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
        final var tokenRelStore = handleContext.readableStore(ReadableTokenRelationStore.class);
        final var accountStore = handleContext.readableStore(ReadableAccountStore.class);
        final var config = handleContext.configuration();
        final var recordBuilder = handleContext.recordBuilder(CryptoTransferRecordBuilder.class);
        final var autoCreatedIds = transferContext.resolutions().values();
        final var result = assessFees(tokenStore, tokenRelStore, config, accountStore, autoCreatedIds::contains);

        recordBuilder.assessedCustomFees(result.assessedCustomFees());
        customFeeAssessor.resetInitialNftChanges();
        return result.assessedTxns();
    }

    /**
     * Given a transaction body, assess custom fees for the crypto transfer operation.
     * It iterates through the token transfer list and assesses custom fees for each token transfer
     * It creates 2 new hashmaps with assessed hbar fees and assessed token fees.
     * It is possible the assessed token custom fees could trigger custom fee again.
     * So, we repeat the process for the assessed custom fees one more time.
     * This will be run once in handle and once in ingest to get fees for CryptoTransfer transaction.
     *
     * @param tokenStore - token store
     * @param tokenRelStore - token relation store
     * @param config - configuration
     * @param accountStore - account store
     * @param autoCreationTest - predicate to test if account id is being auto created
     * @return - transaction body with assessed custom fees
     */
    public CustomFeeAssessmentResult assessFees(
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelStore,
            @NonNull final Configuration config,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final Predicate<AccountID> autoCreationTest) {
        final var ledgerConfig = config.getConfigData(LedgerConfig.class);
        final var tokensConfig = config.getConfigData(TokensConfig.class);
        final var maxTransfersAllowed = ledgerConfig.xferBalanceChangesMaxLen();
        final var maxCustomFeeDepth = tokensConfig.maxCustomFeeDepth();

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
            final var result = assessCustomFeesFrom(
                    hbarTransfers,
                    tokenTransfers,
                    tokenStore,
                    tokenRelStore,
                    maxTransfersAllowed,
                    accountStore,
                    autoCreationTest);
            // when there are adjustments made to given transaction, need to re-build the transaction
            if (!result.getAssessedCustomFees().isEmpty()) {
                final var modifiedInputBody = changedInputTxn(txnToAssess, result);
                assessedTxns.add(modifiedInputBody);

                validateTotalAdjustments(modifiedInputBody, maxTransfersAllowed);
                customFeesAssessed.addAll(result.getAssessedCustomFees());

                // build body from assessed custom fees to be fed to next level of assessment
                txnToAssess = buildBodyFromAdjustments(result);
            } else {
                // If there are no assessed custom fee for the input transaction, this will be added to the
                // list of assessed and exit since this doesn't trigger any more custom fees
                break;
            }
            tokenTransfers = txnToAssess.tokenTransfersOrElse(emptyList());
            hbarTransfers = txnToAssess.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());
            levelNum++;
        } while (!tokenTransfers.isEmpty() && levelNum <= MAX_PLAUSIBLE_LEVEL_NUM);

        if (levelNum > MAX_PLAUSIBLE_LEVEL_NUM) {
            log.error("Recursive charging exceeded maximum plausible depth for transaction {}", op);
            throw new IllegalStateException("Custom fee charging exceeded max recursion depth");
        }

        // If the last charging level assessed fees, we should include them for further steps of CryptoTransfer
        if (!hbarTransfers.isEmpty() || !tokenTransfers.isEmpty()) {
            assessedTxns.add(txnToAssess);
        }
        return new CustomFeeAssessmentResult(assessedTxns, customFeesAssessed);
    }

    /**
     * Record to hold the result of custom fee assessment.
     *
     * @param assessedTxns - list of assessed cryptoTransfer transactions
     * @param assessedCustomFees - list of assessed custom fees
     */
    private record CustomFeeAssessmentResult(
            List<CryptoTransferTransactionBody> assessedTxns, List<AssessedCustomFee> assessedCustomFees) {}

    private void validateTotalAdjustments(final CryptoTransferTransactionBody op, final int maxTransfersDepth) {
        final var hbarTransfers = op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList()).stream()
                .map(AccountAmount::accountID)
                .collect(Collectors.toSet())
                .size();
        var fungibleTokenChanges = 0;
        var nftTransfers = 0;
        for (final var xfer : op.tokenTransfersOrElse(emptyList())) {
            fungibleTokenChanges += xfer.transfersOrElse(emptyList()).stream()
                    .map(AccountAmount::accountID)
                    .collect(Collectors.toSet())
                    .size();
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
        for (final var xfers : op.tokenTransfersOrElse(emptyList())) {
            final var token = xfers.token();
            // If there are no changes for the token, leave its list untouched
            if (!changedFungibleTokenTransfers.containsKey(token)) {
                tokenTransferLists.add(xfers);
            } else {
                final var postAssessmentBalances = changedFungibleTokenTransfers.get(token);
                final var adjustsHere = xfers.transfersOrThrow();
                final var includedNetNewChanges = postAssessmentBalances.size() > adjustsHere.size();
                if (includedNetNewChanges || balancesChangedBetween(adjustsHere, postAssessmentBalances)) {
                    final List<AccountAmount> newTransfers = new ArrayList<>(adjustsHere.size());
                    // First re-use the original transaction body to preserve any approvals or decimals that were set
                    for (final var aa : adjustsHere) {
                        final var newAmount = postAssessmentBalances.getOrDefault(aa.accountID(), aa.amount());
                        newTransfers.add(aa.copyBuilder().amount(newAmount).build());
                        postAssessmentBalances.remove(aa.accountID());
                    }
                    // Add any net-new custom fee adjustments (e.g. credits to fee collectors)
                    for (final var entry : postAssessmentBalances.entrySet()) {
                        newTransfers.add(AccountAmount.newBuilder()
                                .accountID(entry.getKey())
                                .amount(entry.getValue())
                                .build());
                    }
                    tokenTransferLists.add(
                            xfers.copyBuilder().transfers(newTransfers).build());
                } else {
                    tokenTransferLists.add(xfers);
                }
            }
        }
        copy.tokenTransfers(tokenTransferLists);
        return copy.build();
    }

    private boolean balancesChangedBetween(
            @NonNull final List<AccountAmount> original, @NonNull final Map<AccountID, Long> postAssessmentBalances) {
        for (final var aa : original) {
            if (aa.amount() != postAssessmentBalances.getOrDefault(aa.accountID(), aa.amount())) {
                return true;
            }
        }
        return false;
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
            @NonNull final ReadableTokenRelationStore tokenRelStore,
            final int maxTransfersSize,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final Predicate<AccountID> autoCreationTest) {
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

                final boolean isFungible = token.tokenType().equals(FUNGIBLE_COMMON);
                validateFalse(
                        !isFungible && adjustment != 0, ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON);

                if (adjustment < 0) {
                    final var sender = aa.accountID();
                    // If sender for this adjustment is same as treasury for token
                    // then don't charge any custom fee. Since token treasuries are exempt from custom fees
                    if (feeMeta.treasuryId().equals(sender)) {
                        continue;
                    }
                    customFeeAssessor.assess(
                            sender,
                            feeMeta,
                            maxTransfersSize,
                            null,
                            result,
                            tokenRelStore,
                            accountStore,
                            autoCreationTest);
                }
            }

            for (final var nftTransfer : nftTransfers) {
                if (feeMeta.treasuryId().equals(nftTransfer.senderAccountID())) {
                    continue;
                }
                customFeeAssessor.assess(
                        nftTransfer.senderAccountID(),
                        feeMeta,
                        maxTransfersSize,
                        nftTransfer.receiverAccountID(),
                        result,
                        tokenRelStore,
                        accountStore,
                        autoCreationTest);
            }
        }
        return result;
    }
}
