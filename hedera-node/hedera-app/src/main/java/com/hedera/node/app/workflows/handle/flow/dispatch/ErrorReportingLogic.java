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

package com.hedera.node.app.workflows.handle.flow.dispatch;

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.hapi.util.HapiUtils.isHollow;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.NO_DUPLICATE;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.workflows.InsufficientNonFeeDebitsException;
import com.hedera.node.app.spi.workflows.InsufficientServiceFeeException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ErrorReportingLogic {
    private final SolvencyPreCheck solvencyPreCheck;
    private final HederaRecordCache recordCache;
    private final TransactionChecker transactionChecker;

    @Inject
    public ErrorReportingLogic(
            final SolvencyPreCheck solvencyPreCheck,
            final HederaRecordCache recordCache,
            final TransactionChecker transactionChecker) {
        this.solvencyPreCheck = solvencyPreCheck;
        this.recordCache = recordCache;
        this.transactionChecker = transactionChecker;
    }

    public ErrorReport errorReportFor(@NonNull final Dispatch dispatch) {
        final var creatorError = creatorErrorIfKnown(dispatch);
        if (creatorError != null) {
            return ErrorReport.withCreatorError(dispatch.creatorInfo().accountId(), creatorError);
        } else {
            final var payer = getPayer(dispatch);
            final var category = dispatch.txnCategory();
            final var requiresPayerSig = category == USER || category == SCHEDULED;
            if (requiresPayerSig && !isHollow(payer)) {
                // Skip payer verification for hollow accounts because ingest only submits valid signatures
                // for hollow payers; and if an account is still hollow here, its alias cannot have changed
                final var verification = dispatch.keyVerifier().verificationFor(payer.keyOrThrow());
                if (verification.failed()) {
                    return ErrorReport.withCreatorError(dispatch.creatorInfo().accountId(), INVALID_PAYER_SIGNATURE);
                }
            }
            final var duplicateCheckResult = category != USER
                    ? NO_DUPLICATE
                    : recordCache.hasDuplicate(
                            dispatch.txnInfo().txBody().transactionIDOrThrow(),
                            dispatch.creatorInfo().nodeId());
            return switch (duplicateCheckResult) {
                case NO_DUPLICATE -> checkSolvencyOfPayer(payer, false, dispatch);
                case SAME_NODE -> ErrorReport.withCreatorError(
                        dispatch.creatorInfo().accountId(), DUPLICATE_TRANSACTION);
                case OTHER_NODE -> checkSolvencyOfPayer(payer, true, dispatch);
            };
        }
    }

    @NonNull
    private ErrorReport checkSolvencyOfPayer(final Account payer, boolean isDuplicate, final Dispatch dispatch) {
        final var creatorId = dispatch.creatorInfo().accountId();
        try {
            solvencyPreCheck.checkSolvency(dispatch.txnInfo(), payer, dispatch.fees(), false);
        } catch (final InsufficientServiceFeeException e) {
            return ErrorReport.withPayerError(creatorId, payer, e.responseCode(), true, isDuplicate);
        } catch (final InsufficientNonFeeDebitsException e) {
            return ErrorReport.withPayerError(creatorId, payer, e.responseCode(), false, isDuplicate);
        } catch (final PreCheckException e) {
            // Includes InsufficientNetworkFeeException
            return ErrorReport.withCreatorError(creatorId, e.responseCode());
        }
        return ErrorReport.withNoError(creatorId, payer);
    }

    private Account getPayer(final Dispatch dispatch) {
        try {
            return solvencyPreCheck.getPayerAccount(
                    dispatch.readableStoreFactory(), dispatch.syntheticPayer(), dispatch.txnCategory() == CHILD);
        } catch (Exception e) {
            throw new IllegalStateException("Missing payer should be a due diligence failure", e);
        }
    }

    @Nullable
    private ResponseCodeEnum creatorErrorIfKnown(@NonNull final Dispatch dispatch) {
        final var preHandleResult = dispatch.preHandleResult();
        if (preHandleResult == null || preHandleResult.status() == PreHandleResult.Status.SO_FAR_SO_GOOD) {
            return getExpiryError(dispatch);
        }
        return preHandleResult.responseCode();
    }

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
}
