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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.HederaFunctionality.SYSTEM_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;
import static com.hedera.node.app.workflows.handle.HandleWorkflow.ALERT_MESSAGE;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.DuplicateStatus.DUPLICATE;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.ServiceFeeStatus.UNABLE_TO_PAY_SERVICE_FEE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.service.contract.impl.handlers.EthereumTransactionHandler;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.dispatch.DispatchValidator;
import com.hedera.node.app.workflows.handle.dispatch.RecordFinalizer;
import com.hedera.node.app.workflows.handle.dispatch.ValidationResult;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.handle.steps.PlatformStateUpdates;
import com.hedera.node.app.workflows.handle.steps.SystemFileUpdates;
import com.hedera.node.app.workflows.handle.throttle.DispatchUsageManager;
import com.hedera.node.app.workflows.handle.throttle.ThrottleException;
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
    private final DispatchValidator validator;
    private final RecordFinalizer recordFinalizer;
    private final SystemFileUpdates systemFileUpdates;
    private final PlatformStateUpdates platformStateUpdates;
    private final DispatchUsageManager dispatchUsageManager;
    private final ExchangeRateManager exchangeRateManager;
    private final TransactionDispatcher dispatcher;
    private final EthereumTransactionHandler ethereumTransactionHandler;

    @Inject
    public DispatchProcessor(
            @NonNull final Authorizer authorizer,
            @NonNull final DispatchValidator validator,
            @NonNull final RecordFinalizer recordFinalizer,
            @NonNull final SystemFileUpdates systemFileUpdates,
            @NonNull final PlatformStateUpdates platformStateUpdates,
            @NonNull final DispatchUsageManager dispatchUsageManager,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final EthereumTransactionHandler ethereumTransactionHandler) {
        this.authorizer = requireNonNull(authorizer);
        this.validator = requireNonNull(validator);
        this.recordFinalizer = requireNonNull(recordFinalizer);
        this.systemFileUpdates = requireNonNull(systemFileUpdates);
        this.platformStateUpdates = requireNonNull(platformStateUpdates);
        this.dispatchUsageManager = requireNonNull(dispatchUsageManager);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
        this.dispatcher = requireNonNull(dispatcher);
        this.ethereumTransactionHandler = requireNonNull(ethereumTransactionHandler);
    }

    /**
     * This method is responsible for charging the fees and tries to execute the
     * business logic for the given dispatch, guaranteeing that the changes committed
     * to its stack are exactly reflected in its recordBuilder. At the end, it will
     * finalize the record and commit the stack.
     *
     * @param dispatch the dispatch to be processed
     */
    public void processDispatch(@NonNull final Dispatch dispatch) {
        requireNonNull(dispatch);
        final var errorReport = validator.validationReportFor(dispatch);
        if (errorReport.isCreatorError()) {
            chargeCreator(dispatch, errorReport);
        } else {
            chargePayer(dispatch, errorReport);
            if (!alreadyFailed(dispatch, errorReport)) {
                tryHandle(dispatch, errorReport);
            }
        }
        dispatchUsageManager.finalizeAndSaveUsage(dispatch);
        recordFinalizer.finalizeRecord(dispatch);
        if (dispatch.txnCategory() == USER) {
            dispatch.stack().commitTransaction(dispatch.recordBuilder());
        } else {
            dispatch.stack().commitFullStack();
        }
    }

    /**
     * Tries to the transaction logic for the given dispatch. If the logic fails and
     * throws HandleException, it will rollback the stack and charge the payer for the
     * fees. If it is throttled, it will charge the payer for the fees and return
     * FEE_ONLY as work done. If it catches an unexpected exception, it will charge
     * the payer for the fees and return FEE_ONLY as work done.
     *
     * @param dispatch the dispatch to be processed
     * @param validationResult the due diligence report for the dispatch
     * @return the work done by the dispatch
     */
    private void tryHandle(@NonNull final Dispatch dispatch, @NonNull final ValidationResult validationResult) {
        try {
            dispatchUsageManager.screenForCapacity(dispatch);
            dispatcher.dispatchHandle(dispatch.handleContext());
            dispatch.recordBuilder().status(SUCCESS);
            // Only user or preceding transactions can trigger system updates in the current system
            if (dispatch.txnCategory() == USER || dispatch.txnCategory() == PRECEDING) {
                handleSystemUpdates(dispatch);
            }
        } catch (HandleException e) {
            // In case of a ContractCall when it reverts, the gas charged should not be rolled back
            rollback(e.shouldRollbackStack(), e.getStatus(), dispatch.stack(), dispatch.recordBuilder());
            if (e.shouldRollbackStack()) {
                chargePayer(dispatch, validationResult);
            }
            // Since there is no easy way to say how much work was done in the failed dispatch,
            // and current throttling is very rough-grained, we just return USER_TRANSACTION here
        } catch (final ThrottleException e) {
            rollbackAndRechargeFee(dispatch, validationResult, e.getStatus());
            if (dispatch.txnInfo().functionality() == ETHEREUM_TRANSACTION) {
                ethereumTransactionHandler.handleThrottled(dispatch.handleContext());
            }
        } catch (final Exception e) {
            logger.error("{} - exception thrown while handling dispatch", ALERT_MESSAGE, e);
            rollbackAndRechargeFee(dispatch, validationResult, FAIL_INVALID);
        }
    }

    /**
     * Handles the system updates for the dispatch. It will notify the responsible system file update facility if
     * any system file was uploaded. It will also notify if the platform state was updated.
     *
     * @param dispatch the dispatch to be processed
     */
    private void handleSystemUpdates(final Dispatch dispatch) {
        // Notify responsible facility if system-file was uploaded.
        // Returns SUCCESS if no system-file was uploaded
        final var fileUpdateResult = systemFileUpdates.handleTxBody(
                dispatch.stack(), dispatch.txnInfo().txBody());

        // In case we just changed the exchange rates via 0.0.112 update, reset them now
        dispatch.recordBuilder()
                .exchangeRate(exchangeRateManager.exchangeRates())
                .status(fileUpdateResult);

        // Notify if platform state was updated
        platformStateUpdates.handleTxBody(dispatch.stack(), dispatch.txnInfo().txBody());
    }

    /**
     * Handles the exception for the dispatch. It will rollback the stack, charge
     * the payer for the fees and return FEE_ONLY as work done.
     *
     * @param dispatch the dispatch to be processed
     * @param validationResult the due diligence report for the dispatch
     * @param status the status to set
     */
    private void rollbackAndRechargeFee(
            @NonNull final Dispatch dispatch,
            @NonNull final ValidationResult validationResult,
            @NonNull final ResponseCodeEnum status) {
        rollback(true, status, dispatch.stack(), dispatch.recordBuilder());
        chargePayer(dispatch, validationResult.withoutServiceFee());
        dispatchUsageManager.trackFeePayments(dispatch);
    }

    /**
     * Charges the creator for the network fee. This will be called when there is a due diligence failure.
     *
     * @param dispatch the dispatch to be processed
     * @param report the due diligence report for the dispatch
     */
    private void chargeCreator(@NonNull final Dispatch dispatch, @NonNull final ValidationResult report) {
        dispatch.recordBuilder().status(report.creatorErrorOrThrow());
        dispatch.feeAccumulator()
                .chargeNetworkFee(report.creatorId(), dispatch.fees().networkFee());
    }

    /**
     * Charges the payer for the fees. If the payer is unable to pay the service fee, the service fee
     * will be charged to the creator. If the transaction is a duplicate, the service fee will be waived.
     *
     * @param dispatch the dispatch to be processed
     * @param report the due diligence report for the dispatch
     */
    private void chargePayer(@NonNull final Dispatch dispatch, @NonNull final ValidationResult report) {
        final var fees = dispatch.fees();
        if (fees.nothingToCharge()) {
            return;
        }
        final var hasWaivedFees = authorizer.hasWaivedFees(
                dispatch.payerId(),
                dispatch.txnInfo().functionality(),
                dispatch.txnInfo().txBody());
        if (hasWaivedFees) {
            return;
        }
        final var shouldWaiveServiceFee =
                report.serviceFeeStatus() == UNABLE_TO_PAY_SERVICE_FEE || report.duplicateStatus() == DUPLICATE;
        final var feesToCharge = shouldWaiveServiceFee ? fees.withoutServiceComponent() : fees;
        if (dispatch.txnCategory() == USER) {
            dispatch.feeAccumulator()
                    .chargeFees(report.payerOrThrow().accountIdOrThrow(), report.creatorId(), feesToCharge);
        } else {
            // The node only does work for submitting user transactions, so for other categories,
            // we charge fees that are collected without a disbursement to the node account
            dispatch.feeAccumulator()
                    .chargeNetworkFee(report.payerOrThrow().accountIdOrThrow(), feesToCharge.totalFee());
        }
    }

    /**
     * Rolls back the stack and sets the status of the transaction in case of a failure.
     *
     * @param rollbackStack whether to rollback the stack. Will be false when the failure is due to a
     * {@link HandleException} that is due to a contract call revert.
     * @param status the status to set
     * @param stack the save point stack to rollback
     */
    private void rollback(
            final boolean rollbackStack,
            @NonNull final ResponseCodeEnum status,
            @NonNull final SavepointStackImpl stack,
            @NonNull final StreamBuilder builder) {
        builder.status(status);
        if (rollbackStack) {
            stack.rollbackFullStack();
        }
    }

    /**
     * Checks if the transaction has already failed due to an error that can be identified before even performing
     * the dispatch. If it has, it will set the status of the dispatch's record builder and return true.
     * Otherwise, it will return false.
     *
     * @param dispatch the dispatch to be processed
     * @param validationResult the due diligence report for the dispatch
     * @return true if the transaction has already failed, false otherwise
     */
    private boolean alreadyFailed(@NonNull final Dispatch dispatch, @NonNull final ValidationResult validationResult) {
        if (validationResult.isPayerError()) {
            dispatch.recordBuilder().status(validationResult.payerErrorOrThrow());
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
     *
     * @param dispatch the dispatch to be processed
     */
    private @Nullable ResponseCodeEnum maybeAuthorizationFailure(@NonNull final Dispatch dispatch) {
        if (!authorizer.isAuthorized(dispatch.payerId(), dispatch.txnInfo().functionality())) {
            return dispatch.txnInfo().functionality() == SYSTEM_DELETE ? NOT_SUPPORTED : UNAUTHORIZED;
        }
        final var failure = authorizer.hasPrivilegedAuthorization(
                dispatch.payerId(),
                dispatch.txnInfo().functionality(),
                dispatch.txnInfo().txBody());
        return switch (failure) {
            case UNAUTHORIZED -> AUTHORIZATION_FAILED;
            case IMPERMISSIBLE -> ENTITY_NOT_ALLOWED_TO_DELETE;
            default -> null;
        };
    }

    /**
     * Asserts that the signatures are valid. If the signatures are not valid, it will throw a {@link HandleException}.
     *
     * @param dispatch the dispatch to be processed
     */
    private boolean failsSignatureVerification(@NonNull final Dispatch dispatch) {
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
}
