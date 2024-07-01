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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.LazyCreationConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

public class CryptoTransferExecutor {

    public static void executeTransfer(
            TransactionBody txn,
            TransferContextImpl transferContext,
            HandleContext context,
            CryptoTransferValidator validator,
            CryptoTransferRecordBuilder recordBuilder) {
        final var topLevelPayer = context.payer();
        // Use the op with replaced aliases in further steps
        transferContext.validateHbarAllowances();

        // Replace all aliases in the transaction body with its account ids
        final var replacedOp = ensureAndReplaceAliasesInOp(txn, transferContext, context, validator);
        // Use the op with replaced aliases in further steps
        final var steps = decomposeIntoSteps(replacedOp, topLevelPayer, transferContext);
        for (final var step : steps) {
            // Apply all changes to the handleContext's States
            step.doIn(transferContext);
        }
        if (!transferContext.getAutomaticAssociations().isEmpty()) {
            transferContext.getAutomaticAssociations().forEach(recordBuilder::addAutomaticTokenAssociation);
        }
        if (!transferContext.getAssessedCustomFees().isEmpty()) {
            recordBuilder.assessedCustomFees(transferContext.getAssessedCustomFees());
        }
    }

    /**
     * Ensures all aliases specified in the transfer exist. If the aliases are in receiver section, and don't exist
     * they will be auto-created. This step populates resolved aliases and number of auto creations in the
     * transferContext, which is used by subsequent steps and throttling.
     * It will also replace all aliases in the {@link CryptoTransferTransactionBody} with its account ids, so it will
     * be easier to process in next steps.
     * @param txn the given transaction body
     * @param transferContext the given transfer context
     * @param context the given handle context
     * @return the replaced transaction body with all aliases replaced with its account ids
     * @throws HandleException if any error occurs during the process
     */
    private static CryptoTransferTransactionBody ensureAndReplaceAliasesInOp(
            @NonNull final TransactionBody txn,
            @NonNull final TransferContextImpl transferContext,
            @NonNull final HandleContext context,
            @NonNull final CryptoTransferValidator validator)
            throws HandleException {
        final var op = txn.cryptoTransferOrThrow();

        // ensure all aliases exist, if not create then if receivers
        ensureExistenceOfAliasesOrCreate(op, transferContext);
        if (transferContext.numOfLazyCreations() > 0) {
            final var config = context.configuration().getConfigData(LazyCreationConfig.class);
            validateTrue(config.enabled(), NOT_SUPPORTED);
        }

        // replace all aliases with its account ids, so it will be easier to process in next steps
        final var replacedOp = new ReplaceAliasesWithIDsInOp().replaceAliasesWithIds(op, transferContext);
        // re-run pure checks on this op to see if there are no duplicates
        try {
            final var txnBody = txn.copyBuilder().cryptoTransfer(replacedOp).build();
            transferPureChecks(validator, txnBody);
        } catch (PreCheckException e) {
            throw new HandleException(e.responseCode());
        }
        return replacedOp;
    }

    private static void ensureExistenceOfAliasesOrCreate(
            @NonNull final CryptoTransferTransactionBody op, @NonNull final TransferContextImpl transferContext) {
        final var ensureAliasExistence = new EnsureAliasesStep(op);
        ensureAliasExistence.doIn(transferContext);
    }

    /**
     * Decomposes a crypto transfer into a sequence of steps that can be executed in order.
     * Each step validates the preconditions needed from TransferContextImpl in order to perform its action.
     * Steps are as follows:
     * <ol>
     *     <li>(c,o)Ensure existence of alias-referenced accounts</li>
     *     <li>(+,c)Charge custom fees for token transfers</li>
     *     <li>(o)Ensure associations of token recipients</li>
     *     <li>(+)Do zero-sum hbar balance changes</li>
     *     <li>(+)Do zero-sum fungible token transfers</li>
     *     <li>(+)Change NFT owners</li>
     *     <li>(+,c)Pay staking rewards, possibly to previously unmentioned stakee accounts</li>
     * </ol>
     * LEGEND: '+' = creates new BalanceChange(s) from either the transaction body, custom fee schedule, or staking reward situation
     *        'c' = updates an existing BalanceChange
     *        'o' = causes a side effect not represented as BalanceChange
     *
     * @param op              The crypto transfer transaction body
     * @param topLevelPayer   The payer of the transaction
     * @param transferContext
     * @return A list of steps to execute
     */
    private static List<TransferStep> decomposeIntoSteps(
            final CryptoTransferTransactionBody op,
            final AccountID topLevelPayer,
            final TransferContextImpl transferContext) {
        final List<TransferStep> steps = new ArrayList<>();
        // Step 1: associate any token recipients that are not already associated and have
        // auto association slots open
        steps.add(new AssociateTokenRecipientsStep(op));
        // Step 2: Charge custom fees for token transfers
        final var customFeeStep = new CustomFeeAssessmentStep(op);
        // The below steps should be doe for both custom fee assessed transaction in addition to
        // original transaction
        final var customFeeAssessedOps = customFeeStep.assessCustomFees(transferContext);

        for (final var txn : customFeeAssessedOps) {
            steps.add(new AssociateTokenRecipientsStep(txn));
            // Step 3: Charge hbar transfers and also ones with isApproval. Modify the allowances map on account
            final var assessHbarTransfers = new AdjustHbarChangesStep(txn, topLevelPayer);
            steps.add(assessHbarTransfers);

            // Step 4: Charge token transfers with an approval. Modify the allowances map on account
            final var assessFungibleTokenTransfers =
                    new AdjustFungibleTokenChangesStep(txn.tokenTransfers(), topLevelPayer);
            steps.add(assessFungibleTokenTransfers);

            // Step 5: Change NFT owners and also ones with isApproval. Clear the spender on NFT.
            // Will be a no-op for every txn except possibly the first (i.e., the top-level txn).
            // This is because assessed custom fees never change NFT owners
            final var changeNftOwners = new NFTOwnersChangeStep(txn.tokenTransfers(), topLevelPayer);
            steps.add(changeNftOwners);
        }

        return steps;
    }

    public static void transferPureChecks(
            @NonNull CryptoTransferValidator validator, @NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(validator);
        requireNonNull(txn);
        final var op = txn.cryptoTransfer();
        validateTruePreCheck(op != null, INVALID_TRANSACTION_BODY);
        validator.pureChecks(op);
    }

    public static void doPreHandle(
            CryptoTransferTransactionBody op, ReadableTokenStore tokenStore, ReadableAccountStore accountStore) {
        // todo sync with Ivan to check if we need pre handle extracted from CryptoTransferHandler...

        //        for (final var transfers : op.tokenTransfers()) {
        //            final var tokenMeta = tokenStore.getTokenMeta(transfers.tokenOrElse(TokenID.DEFAULT));
        //            if (tokenMeta == null) throw new PreCheckException(INVALID_TOKEN_ID);
        //            checkFungibleTokenTransfers(transfers.transfers(), context, accountStore, false);
        //            checkNftTransfers(transfers.nftTransfers(), context, tokenMeta, op, accountStore);
        //        }
        //
        //        final var hbarTransfers = op.transfersOrElse(TransferList.DEFAULT).accountAmounts();
        //        checkFungibleTokenTransfers(hbarTransfers, context, accountStore, true);
    }
}
