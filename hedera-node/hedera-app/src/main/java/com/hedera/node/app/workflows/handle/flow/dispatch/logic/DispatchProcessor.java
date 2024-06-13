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

import static com.hedera.hapi.node.base.HederaFunctionality.SYSTEM_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.workflows.handle.flow.txn.WorkDone.FEES_ONLY;
import static com.hedera.node.app.workflows.handle.flow.txn.WorkDone.USER_TRANSACTION;
import static com.hedera.node.app.workflows.handle.flow.util.FlowUtils.ALERT_MESSAGE;
import static com.hedera.node.app.workflows.handle.flow.util.FlowUtils.isContractOperation;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.PlatformStateUpdateFacility;
import com.hedera.node.app.workflows.handle.SystemFileUpdateFacility;
import com.hedera.node.app.workflows.handle.flow.dispatch.Dispatch;
import com.hedera.node.app.workflows.handle.flow.txn.WorkDone;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class has the common logic that is executed for a user dispatch and a child dispatch transactions.
 * This will charge the fees to the creator if there is a node due diligence failure. Otherwise, charges the fees to the
 * payer and executes the business logic for the given dispatch, guaranteeing that the changes committed to its stack
 * are exactly reflected in its recordBuilder. At the end, it will finalize the record and commit the stack.
 */
@Singleton
public class DispatchProcessor {
    private static final Logger logger = LogManager.getLogger(DispatchProcessor.class);

    private final Authorizer authorizer;
    private final ErrorReporter errorReporter;
    private final RecordFinalizer recordFinalizer;
    private final SystemFileUpdateFacility systemFileUpdateFacility;
    private final PlatformStateUpdateFacility platformStateUpdateFacility;
    private final ExchangeRateManager exchangeRateManager;
    private final TransactionDispatcher dispatcher;
    private final NetworkUtilizationManager networkUtilizationManager;

    @Inject
    public DispatchProcessor(
            final Authorizer authorizer,
            final ErrorReporter errorReporter,
            final RecordFinalizer recordFinalizer,
            final SystemFileUpdateFacility systemFileUpdateFacility,
            final PlatformStateUpdateFacility platformStateUpdateFacility,
            final ExchangeRateManager exchangeRateManager,
            final TransactionDispatcher dispatcher,
            final NetworkUtilizationManager networkUtilizationManager) {
        this.authorizer = authorizer;
        this.errorReporter = errorReporter;
        this.recordFinalizer = recordFinalizer;
        this.systemFileUpdateFacility = systemFileUpdateFacility;
        this.platformStateUpdateFacility = platformStateUpdateFacility;
        this.exchangeRateManager = exchangeRateManager;
        this.dispatcher = dispatcher;
        this.networkUtilizationManager = networkUtilizationManager;
    }

    /**
     * This method is responsible for charging the fees and tries to execute the business logic for the given dispatch,
     * guaranteeing that the changes committed to its stack are exactly reflected in its recordBuilder.
     * At the end, it will finalize the record and commit the stack. The WorkDone returned will be used track the
     * network utilization. It will be FEE_ONLY if the transaction has node errors, otherwise it will be USER_TRANSACTION.
     * @param dispatch the dispatch to be processed
     * @return the work done by the dispatch
     */
    public WorkDone processDispatch(@NonNull Dispatch dispatch) {
        final var errorReport = errorReporter.errorReportFor(dispatch);
        var workDone = FEES_ONLY;
        if (errorReport.isCreatorError()) {
            chargeCreator(dispatch, errorReport);
        } else {
            chargePayer(dispatch, errorReport);
            if (!alreadyFailed(dispatch, errorReport)) {
                workDone = tryHandle(dispatch, errorReport);
            }
        }
        recordFinalizer.finalizeRecord(dispatch);
        dispatch.stack().commitFullStack();
        return workDone;
    }

    /**
     * Tries to the transaction logic for the given dispatch. If the logic fails and throws HandleException, it will
     * rollback the stack and charge the payer for the fees.
     * If it is throttled, it will charge the payer for the fees and return FEE_ONLY as work done.
     * If it catches an unexpected exception, it will charge the payer for the fees and return FEE_ONLY as work done.
     * @param dispatch the dispatch to be processed
     * @param errorReport the due diligence report for the dispatch
     * @return the work done by the dispatch
     */
    private WorkDone tryHandle(@NonNull final Dispatch dispatch, @NonNull final ErrorReport errorReport) {
        try {
            final var workDone = handle(dispatch);
            dispatch.recordBuilder().status(SUCCESS);
            handleSystemUpdates(dispatch);
            return workDone;
        } catch (HandleException e) {
            // In case of a ContractCall when it reverts, the gas charged should not be rolled back
            rollback(
                    e.shouldRollbackStack(),
                    e.getStatus(),
                    dispatch.stack(),
                    dispatch.recordListBuilder(),
                    dispatch.recordBuilder());
            if (e.shouldRollbackStack()) {
                chargePayer(dispatch, errorReport);
            }
            return USER_TRANSACTION;
        } catch (ThrottleException e) {
            return handleException(dispatch, errorReport, dispatch.recordListBuilder(), e.getStatus());
        } catch (Exception e) {
            logger.error("{} - exception thrown while handling dispatch", ALERT_MESSAGE, e);
            return handleException(dispatch, errorReport, dispatch.recordListBuilder(), ResponseCodeEnum.FAIL_INVALID);
        }
    }

    /**
     * Handles the system updates for the dispatch. It will notify the responsible system file update facility if
     * any system file was uploaded. It will also notify if the platform state was updated.
     * @param dispatch the dispatch to be processed
     */
    private void handleSystemUpdates(final Dispatch dispatch) {
        // Notify responsible facility if system-file was uploaded.
        // Returns SUCCESS if no system-file was uploaded
        final var fileUpdateResult = systemFileUpdateFacility.handleTxBody(
                dispatch.stack(), dispatch.txnInfo().txBody());

        dispatch.recordBuilder()
                .exchangeRate(exchangeRateManager.exchangeRates())
                .status(fileUpdateResult);

        // Notify if platform state was updated
        platformStateUpdateFacility.handleTxBody(
                dispatch.stack(), dispatch.platformState(), dispatch.txnInfo().txBody());
    }

    /**
     * Handles the exception for the dispatch. It will rollback the stack, charge the payer for the fees and return
     * FEE_ONLY as work done.
     * @param dispatch the dispatch to be processed
     * @param errorReport the due diligence report for the dispatch
     * @param recordListBuilder the record list builder
     * @param status the status to set
     * @return the work done by the dispatch
     */
    @NonNull
    private WorkDone handleException(
            final @NonNull Dispatch dispatch,
            final @NonNull ErrorReport errorReport,
            final @NonNull RecordListBuilder recordListBuilder,
            final ResponseCodeEnum status) {
        rollback(true, status, dispatch.stack(), recordListBuilder, dispatch.recordBuilder());
        chargePayer(dispatch, errorReport.withoutServiceFee());
        return FEES_ONLY;
    }

    /**
     * Charges the creator for the network fee. This will be called when there is a due diligence failure.
     * @param dispatch the dispatch to be processed
     * @param report the due diligence report for the dispatch
     */
    private void chargeCreator(final Dispatch dispatch, ErrorReport report) {
        dispatch.recordBuilder().status(report.creatorErrorOrThrow());
        dispatch.feeAccumulator()
                .chargeNetworkFee(report.creatorId(), dispatch.fees().networkFee());
    }

    /**
     * Charges the payer for the fees. If the payer is unable to pay the service fee, the service fee will be charged to
     * the creator. If the transaction is a duplicate, the service fee will be waived.
     * @param dispatch the dispatch to be processed
     * @param report the due diligence report for the dispatch
     */
    private void chargePayer(final Dispatch dispatch, ErrorReport report) {
        final var hasWaivedFees = authorizer.hasWaivedFees(
                dispatch.syntheticPayer(),
                dispatch.txnInfo().functionality(),
                dispatch.txnInfo().txBody());
        if (hasWaivedFees) {
            return;
        }
        if (report.unableToPayServiceFee() || report.duplicateStatus() == DuplicateStatus.DUPLICATE) {
            dispatch.feeAccumulator()
                    .chargeFees(
                            report.payerOrThrow().accountIdOrThrow(),
                            report.creatorId(),
                            dispatch.fees().withoutServiceComponent());
        } else {
            dispatch.feeAccumulator()
                    .chargeFees(report.payerOrThrow().accountIdOrThrow(), report.creatorId(), dispatch.fees());
        }
    }

    /**
     * Rolls back the stack and sets the status of the transaction in case of a failure.
     *
     * @param rollbackStack whether to rollback the stack. Will be false when the failure is due to a
     * {@link HandleException} that is due to a contract call revert.
     * @param status the status to set
     * @param stack the save point stack to rollback
     * @param recordListBuilder the record list builder to revert
     */
    private void rollback(
            final boolean rollbackStack,
            @NonNull final ResponseCodeEnum status,
            @NonNull final SavepointStackImpl stack,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        if (rollbackStack) {
            stack.rollbackFullStack();
        }
        recordBuilder.status(status);
        recordListBuilder.revertChildrenOf(recordBuilder);
    }

    private WorkDone handle(@NonNull final Dispatch dispatch) {
        if (isContractOperation(dispatch)) {
            networkUtilizationManager.trackTxn(dispatch.txnInfo(), dispatch.consensusNow(), dispatch.stack());
            if (networkUtilizationManager.wasLastTxnGasThrottled()) {
                throw new ThrottleException(CONSENSUS_GAS_EXHAUSTED);
            }
        }
        dispatcher.dispatchHandle(dispatch.handleContext());
        return USER_TRANSACTION;
    }

    private boolean alreadyFailed(@NonNull final Dispatch dispatch, @NonNull final ErrorReport errorReport) {
        if (errorReport.isPayerError()) {
            dispatch.recordBuilder().status(errorReport.payerErrorOrThrow());
            return true;
        }
        final var authorizationFailure = maybeAuthorizationFailure(dispatch);
        if (authorizationFailure != null) {
            dispatch.recordBuilder().status(authorizationFailure);
            return true;
        }
        if (failsSignatureVerification(dispatch)) {
            dispatch.recordBuilder().status(INVALID_SIGNATURE);
            return true;
        }
        return false;
    }

    /**
     * Asserts that the dispatch is authorized. If the dispatch is not authorized, it will throw a {@link HandleException}.
     * @param dispatch the dispatch to be processed
     */
    private @Nullable ResponseCodeEnum maybeAuthorizationFailure(final Dispatch dispatch) {
        if (!authorizer.isAuthorized(
                dispatch.syntheticPayer(), dispatch.txnInfo().functionality())) {
            return dispatch.txnInfo().functionality() == SYSTEM_DELETE ? NOT_SUPPORTED : UNAUTHORIZED;
        }
        final var failure = authorizer.hasPrivilegedAuthorization(
                dispatch.syntheticPayer(),
                dispatch.txnInfo().functionality(),
                dispatch.txnInfo().txBody());
        return switch (failure) {
            case UNAUTHORIZED -> UNAUTHORIZED;
            case IMPERMISSIBLE -> ENTITY_NOT_ALLOWED_TO_DELETE;
            default -> null;
        };
    }

    /**
     * Asserts that the signatures are valid. If the signatures are not valid, it will throw a {@link HandleException}.
     * @param dispatch the dispatch to be processed
     */
    private boolean failsSignatureVerification(final Dispatch dispatch) {
        for (final var key : dispatch.requiredKeys()) {
            final var verification = dispatch.keyVerifier().verificationFor(key);
            if (verification.failed()) {
                return true;
            }
        }
        // If there are any hollow accounts whose signatures need to be verified, verify them
        for (final var hollowAccount : dispatch.hollowAccounts()) {
            final var verification = dispatch.keyVerifier().verificationFor(hollowAccount.alias());
            if (verification.failed()) {
                return true;
            }
        }
        return false;
    }

    /**
     * This class is used to throw a {@link ThrottleException} when a transaction is gas throttled.
     */
    private static class ThrottleException extends RuntimeException {
        private final ResponseCodeEnum status;

        public ThrottleException(final ResponseCodeEnum status) {
            this.status = status;
        }

        public ResponseCodeEnum getStatus() {
            return status;
        }
    }
}
