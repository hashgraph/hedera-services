/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONTRACT_CREATE}.
 */
@Singleton
public class ContractCreateHandler implements TransactionHandler {
    private static final AccountID REMOVE_AUTO_RENEW_ACCOUNT_SENTINEL =
            AccountID.newBuilder().shardNum(0).realmNum(0).accountNum(0).build();

    @Inject
    public ContractCreateHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, and warms the cache.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param context the {@link PreHandleContext} which collects all information
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().contractCreateInstanceOrThrow();

        // The transaction cannot set the admin key unless the transaction was signed by that key,
        // unless the key is a "contract ID" key, in which case we don't gather the signature (since
        // there is not one) and it is a perfectly valid arrangement.
        if (op.hasAdminKey()) {
            final var adminKey = op.adminKeyOrThrow();
            if (!adminKey.hasContractID()) {
                context.requireKey(adminKey);
            }
        }

        // If an account is to be used for auto-renewal, then the account must exist and the transaction
        // must be signed with that account's key.
        if (op.hasAutoRenewAccountId()) {
            final var autoRenewAccountID = op.autoRenewAccountIdOrThrow();
            if (!autoRenewAccountID.equals(REMOVE_AUTO_RENEW_ACCOUNT_SENTINEL)) {
                context.requireKeyOrThrow(autoRenewAccountID, INVALID_AUTORENEW_ACCOUNT);
            }
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
