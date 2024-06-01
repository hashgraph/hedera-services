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

package com.hedera.node.app.workflows.handle.flow.infra;

import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.hapi.util.HapiUtils.isHollow;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.NO_DUPLICATE;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.SAME_NODE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.NODE_DUE_DILIGENCE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PRE_HANDLE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.workflows.InsufficientNonFeeDebitsException;
import com.hedera.node.app.spi.workflows.InsufficientServiceFeeException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.hedera.node.app.workflows.handle.flow.util.ValidationResult;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.state.HederaState;
import java.time.Instant;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HandleValidations {
    private final PreHandleResult preHandleResult;
    private final ReadableStoreFactory readableStoreFactory;
    private final NetworkUtilizationManager utilizationManager;
    private final HandleContextImpl context;
    private final TransactionDispatcher dispatcher;
    private final HederaState state;
    private final DefaultKeyVerifier verifier;
    private final Instant consensusNow;
    private final SolvencyPreCheck solvencyPreCheck;
    private final HederaRecordCache recordCache;
    private final TransactionChecker checker;
    private final Authorizer authorizer;

    @Inject
    public HandleValidations(
            final PreHandleResult preHandleResult,
            final ReadableStoreFactory readableStoreFactory,
            final NetworkUtilizationManager utilizationManager,
            final HandleContextImpl context,
            final TransactionDispatcher dispatcher,
            final HederaState state,
            final DefaultKeyVerifier verifier,
            final Instant consensusNow,
            final SolvencyPreCheck solvencyPreCheck,
            final HederaRecordCache recordCache,
            final TransactionChecker checker,
            final Authorizer authorizer) {
        this.preHandleResult = preHandleResult;
        this.readableStoreFactory = readableStoreFactory;
        this.utilizationManager = utilizationManager;
        // Constructor
        this.context = context;
        this.dispatcher = dispatcher;
        this.state = state;
        this.verifier = verifier;
        this.consensusNow = consensusNow;
        this.solvencyPreCheck = solvencyPreCheck;
        this.recordCache = recordCache;
        this.checker = checker;
        this.authorizer = authorizer;
    }

    public ValidationResult validate(final long nodeID) {
        if (preHandleResult.status() == NODE_DUE_DILIGENCE_FAILURE) {
            utilizationManager.trackFeePayments(consensusNow, state);
            final var fees = dispatcher.dispatchComputeFees(context);
            return new ValidationResult(preHandleResult.status(), preHandleResult.responseCode(), fees);
        }

        final var txInfo = requireNonNull(preHandleResult.txInfo());
        final var payerID = requireNonNull(txInfo.payerID());
        final var functionality = txInfo.functionality();
        final var txBody = txInfo.txBody();
        boolean isPayerHollow;

        final Account payer;
        try {
            payer = solvencyPreCheck.getPayerAccount(readableStoreFactory, payerID);
        } catch (PreCheckException e) {
            throw new IllegalStateException("Missing payer should be a due diligence failure", e);
        }
        isPayerHollow = isHollow(payer);
        // Check all signature verifications. This will also wait, if validation is still ongoing.
        // If the payer is hollow the key will be null, so we skip the payer signature verification.
        if (!isPayerHollow) {
            final var payerKeyVerification = verifier.verificationFor(preHandleResult.getPayerKey());
            if (payerKeyVerification.failed()) {
                utilizationManager.trackFeePayments(consensusNow, state);
                final var fees = dispatcher.dispatchComputeFees(context);
                return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, INVALID_PAYER_SIGNATURE, fees);
            }
        }

        // Notice that above, we computed fees assuming network utilization for
        // just a fee payment. Here we instead calculate fees based on tracking the
        // user transaction. This is for mono-service fidelity, but does not have any
        // particular priority and could be revisited later after diff testing
        utilizationManager.trackTxn(txInfo, consensusNow, state);
        final var fees = dispatcher.dispatchComputeFees(context);

        // Check for duplicate transactions. It is perfectly normal for there to be duplicates -- it is valid for
        // a user to intentionally submit duplicates to multiple nodes as a hedge against dishonest nodes, or for
        // other reasons. If we find a duplicate, we *will not* execute the transaction, we will simply charge
        // the payer (whether the payer from the transaction or the node in the event of a due diligence failure)
        // and create an appropriate record to save in state and send to the record stream.
        final var duplicateCheckResult = recordCache.hasDuplicate(txBody.transactionIDOrThrow(), nodeID);
        if (duplicateCheckResult != NO_DUPLICATE) {
            return new ValidationResult(
                    duplicateCheckResult == SAME_NODE ? NODE_DUE_DILIGENCE_FAILURE : PRE_HANDLE_FAILURE,
                    DUPLICATE_TRANSACTION,
                    fees);
        }

        // Check the status and solvency of the payer (assuming their signature is valid)
        try {
            solvencyPreCheck.checkSolvency(txInfo, payer, fees, false);
        } catch (final InsufficientServiceFeeException e) {
            return new ValidationResult(PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE, e.responseCode(), fees);
        } catch (final InsufficientNonFeeDebitsException e) {
            return new ValidationResult(PRE_HANDLE_FAILURE, e.responseCode(), fees);
        } catch (final PreCheckException e) {
            // Includes InsufficientNetworkFeeException
            return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, e.responseCode(), fees);
        }

        // Check the time box of the transaction
        try {
            checker.checkTimeBox(txBody, consensusNow, TransactionChecker.RequireMinValidLifetimeBuffer.NO);
        } catch (final PreCheckException e) {
            return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, e.responseCode(), fees);
        }

        // Check if the payer has the required permissions
        if (!authorizer.isAuthorized(payerID, functionality)) {
            if (functionality == HederaFunctionality.SYSTEM_DELETE) {
                return new ValidationResult(PRE_HANDLE_FAILURE, NOT_SUPPORTED, fees);
            }
            return new ValidationResult(PRE_HANDLE_FAILURE, UNAUTHORIZED, fees);
        }

        // Check if pre-handle was successful
        if (preHandleResult.status() != SO_FAR_SO_GOOD) {
            return new ValidationResult(preHandleResult.status(), preHandleResult.responseCode(), fees);
        }

        // Check if the transaction is privileged and if the payer has the required privileges
        final var privileges = authorizer.hasPrivilegedAuthorization(payerID, functionality, txBody);
        if (privileges == SystemPrivilege.UNAUTHORIZED) {
            return new ValidationResult(PRE_HANDLE_FAILURE, AUTHORIZATION_FAILED, fees);
        }
        if (privileges == SystemPrivilege.IMPERMISSIBLE) {
            return new ValidationResult(PRE_HANDLE_FAILURE, ENTITY_NOT_ALLOWED_TO_DELETE, fees);
        }

        // verify all the keys
        for (final var key : preHandleResult.getRequiredKeys()) {
            final var verification = verifier.verificationFor(key);
            if (verification.failed()) {
                utilizationManager.trackFeePayments(consensusNow, state);
                return new ValidationResult(PRE_HANDLE_FAILURE, INVALID_SIGNATURE, fees);
            }
        }
        // If there are any hollow accounts whose signatures need to be verified, verify them
        for (final var hollowAccount : preHandleResult.getHollowAccounts()) {
            final var verification = verifier.verificationFor(hollowAccount.alias());
            if (verification.failed()) {
                utilizationManager.trackFeePayments(consensusNow, state);
                return new ValidationResult(PRE_HANDLE_FAILURE, INVALID_SIGNATURE, fees);
            }
        }

        return new ValidationResult(SO_FAR_SO_GOOD, OK, fees);
    }
}
