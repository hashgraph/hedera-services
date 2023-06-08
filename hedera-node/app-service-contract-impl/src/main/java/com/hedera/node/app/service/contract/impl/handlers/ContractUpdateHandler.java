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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONTRACT_UPDATE}.
 */
@Singleton
public class ContractUpdateHandler implements TransactionHandler {
    @Inject
    public ContractUpdateHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().contractUpdateInstanceOrThrow();

        if (isAdminSigRequired(op)) {
            context.requireKeyOrThrow(op.contractIDOrElse(ContractID.DEFAULT), INVALID_CONTRACT_ID);
        }
        if (hasCryptoAdminKey(op)) {
            context.requireKey(op.adminKeyOrThrow());
        }
        if (op.hasAutoRenewAccountId() && !op.autoRenewAccountIdOrThrow().equals(AccountID.DEFAULT)) {
            context.requireKeyOrThrow(op.autoRenewAccountIdOrThrow(), INVALID_AUTORENEW_ACCOUNT);
        }
    }

    private boolean isAdminSigRequired(final ContractUpdateTransactionBody op) {
        return !op.hasExpirationTime()
                || hasCryptoAdminKey(op)
                || op.hasProxyAccountID()
                || op.hasAutoRenewPeriod()
                || op.hasFileID()
                || op.memoOrElse("").length() > 0;
    }

    private boolean hasCryptoAdminKey(final ContractUpdateTransactionBody op) {
        return op.hasAdminKey() && !op.adminKeyOrThrow().hasContractID();
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
