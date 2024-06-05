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

import static com.hedera.hapi.node.base.HederaFunctionality.SYSTEM_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;

import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.flow.dispatcher.Dispatch;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HandleLogic {
    private final Authorizer authorizer;
    private final TransactionDispatcher dispatcher;

    @Inject
    public HandleLogic(final Authorizer authorizer, final TransactionDispatcher dispatcher) {
        this.authorizer = authorizer;
        this.dispatcher = dispatcher;
    }

    public void handle(Dispatch dispatch) {
        if (isUnAuthorized(dispatch)) return;

        if (dispatch.userError() != OK) {
            dispatch.recordBuilder().status(dispatch.userError());
            return;
        }

        if (hasInvalidSignature(dispatch)) {
            dispatch.recordBuilder().status(INVALID_SIGNATURE);
            return;
        }

        dispatcher.dispatchHandle(dispatch.handleContext());
        // Do business logic
    }

    private boolean isUnAuthorized(final Dispatch dispatch) {
        if (!authorizer.isAuthorized(
                dispatch.syntheticPayer(), dispatch.txnInfo().functionality())) {
            dispatch.recordBuilder()
                    .status(dispatch.txnInfo().functionality() == SYSTEM_DELETE ? NOT_SUPPORTED : UNAUTHORIZED);
            return true;
        }

        final var privileges = authorizer.hasPrivilegedAuthorization(
                dispatch.syntheticPayer(),
                dispatch.txnInfo().functionality(),
                dispatch.txnInfo().txBody());
        if (privileges == SystemPrivilege.UNAUTHORIZED) {
            dispatch.recordBuilder().status(AUTHORIZATION_FAILED);
            return true;
        } else if (privileges == SystemPrivilege.IMPERMISSIBLE) {
            dispatch.recordBuilder().status(ENTITY_NOT_ALLOWED_TO_DELETE);
            return true;
        }
        return false;
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
}
