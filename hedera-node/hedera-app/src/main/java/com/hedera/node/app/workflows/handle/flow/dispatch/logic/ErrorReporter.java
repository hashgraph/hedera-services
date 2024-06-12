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

package com.hedera.node.app.workflows.handle.flow.dispatch.logic;

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.hapi.util.HapiUtils.isHollow;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.NO_DUPLICATE;
import static com.hedera.node.app.workflows.handle.flow.dispatch.logic.ErrorReport.creatorErrorReport;
import static com.hedera.node.app.workflows.handle.flow.dispatch.logic.ErrorReport.errorFreeReport;
import static com.hedera.node.app.workflows.handle.flow.dispatch.logic.ErrorReport.payerDuplicateErrorReport;
import static com.hedera.node.app.workflows.handle.flow.dispatch.logic.ErrorReport.payerErrorReport;
import static com.hedera.node.app.workflows.handle.flow.dispatch.logic.ErrorReport.payerUniqueErrorReport;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.InsufficientNonFeeDebitsException;
import com.hedera.node.app.spi.workflows.InsufficientServiceFeeException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A class that reports errors that occurred during the processing of a transaction.
 * This includes the creator error, the payer error, and whether the payer was unable to pay the service fee.
 * It also records whether the transaction is a duplicate.
 */
@Singleton
public class ErrorReporter {
    private final SolvencyPreCheck solvencyPreCheck;
    private final HederaRecordCache recordCache;
    private final TransactionChecker transactionChecker;

    /**
     * Creates an error reporter with the given dependencies.
     *
     * @param solvencyPreCheck the solvency pre-check
     * @param recordCache the record cache
     * @param transactionChecker the transaction checker
     */
    @Inject
    public ErrorReporter(
            final SolvencyPreCheck solvencyPreCheck,
            final HederaRecordCache recordCache,
            final TransactionChecker transactionChecker) {
        this.solvencyPreCheck = solvencyPreCheck;
        this.recordCache = recordCache;
        this.transactionChecker = transactionChecker;
    }

    /**
     * Reports an error for the given dispatch. This first checks if there is a creator error, and if so, returns it.
     * Otherwise, checks signatures for non-hollow payer. Also checks if the user transaction is duplicate and if payer
     * can pay the fees.
     *
     * @param dispatch the dispatch
     * @return the error report
     */
    public ErrorReport errorReportFor(@NonNull final Dispatch dispatch) {
        final var creatorError = creatorErrorIfKnown(dispatch);
        if (creatorError != null) {
            return creatorErrorReport(dispatch.creatorInfo().accountId(), creatorError);
        } else {
            final var payer =
                    getPayerAccount(dispatch.readableStoreFactory(), dispatch.syntheticPayer(), dispatch.txnCategory());
            final var category = dispatch.txnCategory();
            final var requiresPayerSig = category == USER || category == SCHEDULED;
            if (requiresPayerSig && !isHollow(payer)) {
                // Skip payer verification for hollow accounts because ingest only submits valid signatures
                // for hollow payers; and if an account is still hollow here, its alias cannot have changed
                final var verification = dispatch.keyVerifier().verificationFor(payer.keyOrThrow());
                if (verification.failed()) {
                    return creatorErrorReport(dispatch.creatorInfo().accountId(), INVALID_PAYER_SIGNATURE);
                }
            }
            final var duplicateCheckResult = category != USER
                    ? NO_DUPLICATE
                    : recordCache.hasDuplicate(
                            dispatch.txnInfo().txBody().transactionIDOrThrow(),
                            dispatch.creatorInfo().nodeId());
            return switch (duplicateCheckResult) {
                case NO_DUPLICATE -> finalPayerErrorReport(payer, IsDuplicate.NO, dispatch);
                case SAME_NODE -> creatorErrorReport(dispatch.creatorInfo().accountId(), DUPLICATE_TRANSACTION);
                case OTHER_NODE -> finalPayerErrorReport(payer, IsDuplicate.YES, dispatch);
            };
        }
    }

    /**
     * Checks payer solvency for Schedule and User transactions. If the payer is a super-user, it will not be checked.
     *
     * @param payer the payer account
     * @param isDuplicate whether the transaction is a duplicate
     * @param dispatch the dispatch
     * @return the error report
     */
    @NonNull
    private ErrorReport finalPayerErrorReport(
            @NonNull final Account payer, @NonNull final IsDuplicate isDuplicate, @NonNull final Dispatch dispatch) {
        final var creatorId = dispatch.creatorInfo().accountId();
        try {
            solvencyPreCheck.checkSolvency(
                    dispatch.txnInfo().txBody(),
                    payer.accountIdOrThrow(),
                    dispatch.txnInfo().functionality(),
                    payer,
                    isDuplicate == IsDuplicate.NO
                            ? dispatch.fees()
                            : dispatch.fees().withoutServiceComponent(),
                    false,
                    dispatch.txnCategory() == USER || dispatch.txnCategory() == SCHEDULED);
        } catch (final InsufficientServiceFeeException e) {
            return payerErrorReport(creatorId, payer, e.responseCode(), true, isDuplicate);
        } catch (final InsufficientNonFeeDebitsException e) {
            return payerErrorReport(creatorId, payer, e.responseCode(), false, isDuplicate);
        } catch (final PreCheckException e) {
            // Includes InsufficientNetworkFeeException
            return creatorErrorReport(creatorId, e.responseCode());
        }
        return switch (isDuplicate) {
            case YES -> payerDuplicateErrorReport(creatorId, payer);
            case NO -> dispatch.preHandleResult().status() == SO_FAR_SO_GOOD
                    ? errorFreeReport(creatorId, payer)
                    : payerUniqueErrorReport(
                            creatorId, payer, dispatch.preHandleResult().responseCode());
        };
    }

    /**
     * Returns the response code if there is any error in pre-handle. If there is no error, checks the
     * transaction expiry.
     *
     * @param dispatch the dispatch
     * @return the response code
     */
    @Nullable
    private ResponseCodeEnum creatorErrorIfKnown(@NonNull final Dispatch dispatch) {
        final var preHandleResult = dispatch.preHandleResult();
        return switch (preHandleResult.status()) {
            case NODE_DUE_DILIGENCE_FAILURE -> preHandleResult.responseCode();
            case SO_FAR_SO_GOOD -> getExpiryError(dispatch);
            case UNKNOWN_FAILURE, PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE, PRE_HANDLE_FAILURE -> null;
        };
    }

    /**
     * Checks the transaction expiry only for user transactions. If the transaction is expired, returns the response code.
     *
     * @param dispatch the dispatch
     * @return the response code
     */
    @Nullable
    private ResponseCodeEnum getExpiryError(final @NonNull Dispatch dispatch) {
        if (dispatch.txnCategory() != USER) {
            return null;
        }
        try {
            transactionChecker.checkTimeBox(
                    dispatch.txnInfo().txBody(),
                    dispatch.consensusNow(),
                    TransactionChecker.RequireMinValidLifetimeBuffer.NO);
        } catch (PreCheckException e) {
            return e.responseCode();
        }
        return null;
    }

    /**
     * Returns the payer account for the given account ID. For the user transaction, the account should not be null,
     * deleted, or a smart contract. For the scheduled transaction, the account should not be null or a smart contract,
     * can be deleted.
     * For the child and preceding transactions, the payer account can be null. Because the payer for contract
     * operations can be a token address.
     *
     * @param storeFactory the store factory
     * @param accountID the account ID
     * @param category the transaction category
     * @return the payer account
     */
    private Account getPayerAccount(
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final AccountID accountID,
            @NonNull final HandleContext.TransactionCategory category) {
        final var accountStore = storeFactory.getStore(ReadableAccountStore.class);
        final var account = accountStore.getAccountById(accountID);
        return switch (category) {
            case USER -> {
                if (account == null || account.deleted() || account.smartContract()) {
                    throw new IllegalStateException(
                            "Category " + category + " payer account should have been rejected " + account);
                }
                yield account;
            }
            case CHILD, PRECEDING -> {
                if (account == null) {
                    throw new IllegalStateException(
                            "Category " + category + " payer account should have been rejected " + account);
                }
                yield account;
            }
            case SCHEDULED -> {
                if (account == null || account.smartContract()) {
                    throw new IllegalStateException(
                            "Category " + category + " payer account should have been rejected " + account);
                }
                yield account;
            }
        };
    }
}
