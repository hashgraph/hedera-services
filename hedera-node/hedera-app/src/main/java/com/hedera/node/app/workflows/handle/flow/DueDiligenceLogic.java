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

package com.hedera.node.app.workflows.handle.flow;

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.util.HapiUtils.isHollow;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.NO_DUPLICATE;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.InsufficientNonFeeDebitsException;
import com.hedera.node.app.spi.workflows.InsufficientServiceFeeException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DueDiligenceLogic {
    private final SolvencyPreCheck solvencyPreCheck;
    private final HederaRecordCache recordCache;
    private final TransactionChecker transactionChecker;

    @Inject
    public DueDiligenceLogic(
            final SolvencyPreCheck solvencyPreCheck,
            final HederaRecordCache recordCache,
            final TransactionChecker transactionChecker) {
        this.solvencyPreCheck = solvencyPreCheck;
        this.recordCache = recordCache;
        this.transactionChecker = transactionChecker;
    }

    public ErrorReport dueDiligenceReportFor(@NonNull final Dispatch dispatch) {
        if (dispatch.dueDiligenceInfo().dueDiligenceStatus() != ResponseCodeEnum.OK) {
            return new ErrorReport(null, dispatch.dueDiligenceInfo(), false);
        } else {
            if (dispatch.txnCategory() == HandleContext.TransactionCategory.USER) {
                final var response = checkIfExpired(dispatch);
                if (response != OK) {
                    return new ErrorReport(null, dispatch.dueDiligenceInfo().withReplacementStatus(response), false);
                }
            }

            final var payer = getPayer(dispatch);
            final var isPayerHollow = isHollow(payer);
            if (!isPayerHollow) {
                final var verification = dispatch.keyVerifier().verificationFor(payer.keyOrThrow());
                if (verification.failed()) {
                    return new ErrorReport(
                            null, dispatch.dueDiligenceInfo().withReplacementStatus(INVALID_PAYER_SIGNATURE), false);
                }
            }

            final var duplicateCheckResult = dispatch.txnCategory() != HandleContext.TransactionCategory.USER
                    ? NO_DUPLICATE
                    : recordCache.hasDuplicate(
                            dispatch.txnInfo().txBody().transactionIDOrThrow(),
                            dispatch.creatorInfo().nodeId());
            return switch (duplicateCheckResult) {
                case NO_DUPLICATE -> checkSolvencyOfPayer(payer, false, dispatch);
                case SAME_NODE -> new ErrorReport(
                        null, dispatch.dueDiligenceInfo().withReplacementStatus(DUPLICATE_TRANSACTION), true);
                case OTHER_NODE -> checkSolvencyOfPayer(payer, true, dispatch);
            };
        }
    }

    @NonNull
    private ErrorReport checkSolvencyOfPayer(final Account payer, boolean isDuplicate, final Dispatch dispatch) {
        try {
            solvencyPreCheck.checkSolvency(dispatch.txnInfo(), payer, dispatch.fees(), false);
        } catch (final InsufficientServiceFeeException e) {
            return new ErrorReport(payer, dispatch.dueDiligenceInfo(), isDuplicate, e.responseCode(), true);
        } catch (final InsufficientNonFeeDebitsException e) {
            return new ErrorReport(payer, dispatch.dueDiligenceInfo(), isDuplicate, e.responseCode(), false);
        } catch (final PreCheckException e) {
            // Includes InsufficientNetworkFeeException
            return new ErrorReport(
                    null, dispatch.dueDiligenceInfo().withReplacementStatus(e.responseCode()), isDuplicate);
        }
        return new ErrorReport(payer, dispatch.dueDiligenceInfo(), isDuplicate);
    }

    private ResponseCodeEnum checkIfExpired(final @NonNull Dispatch dispatch) {
        try {
            transactionChecker.checkTimeBox(
                    dispatch.txnInfo().txBody(),
                    dispatch.consensusNow(),
                    TransactionChecker.RequireMinValidLifetimeBuffer.NO);
        } catch (PreCheckException e) {
            return e.responseCode();
        }
        return OK;
    }

    private Account getPayer(final Dispatch dispatch) {
        try {
            return solvencyPreCheck.getPayerAccount(dispatch.readableStoreFactory(), dispatch.syntheticPayer());
        } catch (Exception e) {
            throw new IllegalStateException("Missing payer should be a due diligence failure", e);
        }
    }
}
