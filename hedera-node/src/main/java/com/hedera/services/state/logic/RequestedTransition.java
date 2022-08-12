/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.txns.TransitionRunner;
import com.hedera.services.txns.auth.SystemOpPolicies;
import com.hedera.services.utils.accessors.TxnAccessor;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RequestedTransition {
    private final TransitionRunner transitionRunner;
    private final SystemOpPolicies opPolicies;
    private final TransactionContext txnCtx;
    private final NetworkCtxManager networkCtxManager;
    private final HapiOpPermissions hapiOpPermissions;

    @Inject
    public RequestedTransition(
            final TransitionRunner transitionRunner,
            final SystemOpPolicies opPolicies,
            final TransactionContext txnCtx,
            final NetworkCtxManager networkCtxManager,
            final HapiOpPermissions hapiOpPermissions) {
        this.transitionRunner = transitionRunner;
        this.opPolicies = opPolicies;
        this.txnCtx = txnCtx;
        this.networkCtxManager = networkCtxManager;
        this.hapiOpPermissions = hapiOpPermissions;
    }

    void finishFor(TxnAccessor accessor) {
        final var permissionStatus =
                hapiOpPermissions.permissibilityOf(accessor.getFunction(), accessor.getPayer());
        if (permissionStatus != OK) {
            txnCtx.setStatus(permissionStatus);
            return;
        }
        final var sysAuthStatus = opPolicies.checkAccessor(accessor).asStatus();
        if (sysAuthStatus != OK) {
            txnCtx.setStatus(sysAuthStatus);
            return;
        }
        if (transitionRunner.tryTransition(accessor)) {
            networkCtxManager.finishIncorporating(accessor.getFunction());
        }
    }
}
