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

import static com.hedera.hapi.node.base.HederaFunctionality.SYSTEM_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.workflows.handle.flow.util.FlowUtils.isContractOperation;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.flow.process.WorkDone;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Business logic for handling transactions.
 */
@Singleton
public class HandleLogic {
    private final Authorizer authorizer;
    private final TransactionDispatcher dispatcher;
    private final NetworkUtilizationManager networkUtilizationManager;

    @Inject
    public HandleLogic(
            final Authorizer authorizer,
            final TransactionDispatcher dispatcher,
            final NetworkUtilizationManager networkUtilizationManager) {
        this.authorizer = authorizer;
        this.dispatcher = dispatcher;
        this.networkUtilizationManager = networkUtilizationManager;
    }

    public WorkDone handle(Dispatch dispatch) {
        assertAuthorized(dispatch);

        if (dispatch.userError() != OK) {
            throw new HandleException(dispatch.userError());
        }
        if (hasInvalidSignature(dispatch)) {
            throw new HandleException(INVALID_SIGNATURE);
        }
        if (isContractOperation(dispatch)) {
            networkUtilizationManager.trackTxn(dispatch.txnInfo(), dispatch.consensusNow(), dispatch.stack());
            if (networkUtilizationManager.wasLastTxnGasThrottled()) {
                throw new ThrottleException(CONSENSUS_GAS_EXHAUSTED);
            }
        }
        dispatcher.dispatchHandle(dispatch.handleContext());
        return WorkDone.USER_TRANSACTION;
    }

    private void assertAuthorized(final Dispatch dispatch) {
        if (!authorizer.isAuthorized(
                dispatch.syntheticPayer(), dispatch.txnInfo().functionality())) {
            throw new HandleException(
                    dispatch.txnInfo().functionality() == SYSTEM_DELETE ? NOT_SUPPORTED : UNAUTHORIZED);
        }

        final var privileges = authorizer.hasPrivilegedAuthorization(
                dispatch.syntheticPayer(),
                dispatch.txnInfo().functionality(),
                dispatch.txnInfo().txBody());
        if (privileges == SystemPrivilege.UNAUTHORIZED) {
            throw new HandleException(AUTHORIZATION_FAILED);
        } else if (privileges == SystemPrivilege.IMPERMISSIBLE) {
            throw new HandleException(ENTITY_NOT_ALLOWED_TO_DELETE);
        }
    }

    private static boolean hasInvalidSignature(final Dispatch dispatch) {
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

    public static class ThrottleException extends RuntimeException {
        private final ResponseCodeEnum status;

        public ThrottleException(final ResponseCodeEnum status) {
            this.status = status;
        }

        public ResponseCodeEnum getStatus() {
            return status;
        }
    }
}
