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

import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.LazyCreationConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Service class that provides static methods for execution of Crypto transfer transaction. The main purpose of this
 * class is reusing the crypto transfer steps logic in to
 * {@link com.hedera.node.app.service.token.impl.handlers.TokenAirdropHandler} It also adds the possibility to separate
 * custom fee assessment steps from other steps (to prepay fees in case of pending airdrops)
 */
@Singleton
public class CryptoTransferExecutor {

    /**
     * Default constructor for injection.
     */
    @Inject
    public CryptoTransferExecutor() {
        // For Dagger injection
    }

    /**
     * Executes all crypto transfer steps.
     *
     * @param txn transaction body
     * @param transferContext transfer context
     * @param context handle context
     * @param validator crypto transfer validator
     * @param recordBuilder record builder
     */
    public void executeCryptoTransfer(
            TransactionBody txn,
            TransferContextImpl transferContext,
            HandleContext context,
            CryptoTransferValidator validator,
            CryptoTransferRecordBuilder recordBuilder) {
        executeCryptoTransfer(txn, transferContext, context, validator, recordBuilder, false);
    }

    /**
     * Charges only the custom fees if any. Used when custom fees should be prepaid in
     * {@link com.hedera.node.app.service.token.impl.handlers.TokenAirdropHandler}
     *
     * @param txn             transaction body
     * @param transferContext transfer context
     */
    public void chargeCustomFee(TransactionBody txn, TransferContextImpl transferContext) {
        final var customFeeStep = new CustomFeeAssessmentStep(txn.cryptoTransferOrThrow());
        var transferBodies = customFeeStep.assessCustomFees(transferContext);
        var topLevelPayer = transferContext.getHandleContext().payer();
        // we skip the origin (first) txn body,
        // so we can adjust changes, that are related only to the custom fees
        for (int i = 1, n = transferBodies.size(); i < n; i++) {
            // adjust balances
            var adjustHbarChangesStep = new AdjustHbarChangesStep(transferBodies.get(i), topLevelPayer);
            adjustHbarChangesStep.doIn(transferContext);
            var adjustFungibleChangesStep =
                    new AdjustFungibleTokenChangesStep(transferBodies.get(i).tokenTransfers(), topLevelPayer);
            adjustFungibleChangesStep.doIn(transferContext);
        }
    }

    /**
     * Executes crypto transfer, but skip custom fee steps. Used when custom fees should be prepaid in
     * {@link com.hedera.node.app.service.token.impl.handlers.TokenAirdropHandler}
     *
     * @param txn             transaction body
     * @param transferContext transfer context
     * @param context         handle context
     * @param validator       crypto transfer validator
     * @param recordBuilder   record builder
     */
    public void executeCryptoTransferWithoutCustomFee(
            TransactionBody txn,
            TransferContextImpl transferContext,
            HandleContext context,
            CryptoTransferValidator validator,
            CryptoTransferRecordBuilder recordBuilder) {
        executeCryptoTransfer(txn, transferContext, context, validator, recordBuilder, true);
    }

    /**
     * Execute crypto transfer transaction
     *
     * @param txn transaction body
     * @param transferContext transfer context
     * @param context handle context
     * @param validator crypto transfer validator
     * @param recordBuilder crypto transfer record builder
     * @param skipCustomFee should execute custom fee steps
     */
    private void executeCryptoTransfer(
            TransactionBody txn,
            TransferContextImpl transferContext,
            HandleContext context,
            CryptoTransferValidator validator,
            CryptoTransferRecordBuilder recordBuilder,
            boolean skipCustomFee) {
        final var topLevelPayer = context.payer();
        // Use the op with replaced aliases in further steps
        transferContext.validateHbarAllowances();

        // Replace all aliases in the transaction body with its account ids
        final var replacedOp = ensureAndReplaceAliasesInOp(txn, transferContext, context, validator);
        // Use the op with replaced aliases in further steps
        final var steps = decomposeIntoSteps(replacedOp, topLevelPayer, transferContext, skipCustomFee);
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
     * @param validator crypto transfer validator
     * @return the replaced transaction body with all aliases replaced with its account ids
     * @throws HandleException if any error occurs during the process
     */
    private CryptoTransferTransactionBody ensureAndReplaceAliasesInOp(
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
            validator.cryptoTransferPureChecks(replacedOp);
        } catch (PreCheckException e) {
            throw new HandleException(e.responseCode());
        }
        return replacedOp;
    }

    private void ensureExistenceOfAliasesOrCreate(
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
    private List<TransferStep> decomposeIntoSteps(
            final CryptoTransferTransactionBody op,
            final AccountID topLevelPayer,
            final TransferContextImpl transferContext,
            boolean skipCustomFees) {
        final List<TransferStep> steps = new ArrayList<>();
        // Step 1: associate any token recipients that are not already associated and have
        // auto association slots open
        steps.add(new AssociateTokenRecipientsStep(op));
        // Step 2: Charge custom fees for token transfers
        final var customFeeStep = new CustomFeeAssessmentStep(op);

        List<CryptoTransferTransactionBody> txns = List.of(op);
        if (!skipCustomFees) {
            txns = customFeeStep.assessCustomFees(transferContext);
        }

        // The below steps should be doe for both custom fee assessed transaction in addition to
        // original transaction
        for (final var txn : txns) {
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
}
