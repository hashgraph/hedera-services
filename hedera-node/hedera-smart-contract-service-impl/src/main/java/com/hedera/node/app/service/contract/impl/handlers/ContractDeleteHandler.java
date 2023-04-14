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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONTRACT_DELETE}.
 */
@Singleton
public class ContractDeleteHandler implements TransactionHandler {
    @Inject
    public ContractDeleteHandler() {
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
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().contractDeleteInstanceOrThrow();
        // The contract ID must be present on the transaction
        final var contractID = op.contractID();
        mustExist(contractID, INVALID_CONTRACT_ID);
        // A contract corresponding to that contract ID must exist in state (otherwise we have
        // nothing to delete)
        final var contract = context.accountAccess().getContractById(contractID);
        mustExist(contract, INVALID_CONTRACT_ID);
        // If there is not an admin key, then the contract is immutable. Otherwise, the transaction
        // must
        // be signed by the admin key.
        context.requireKeyOrThrow(contract.key(), MODIFYING_IMMUTABLE_CONTRACT);
        // If there is a transfer account ID, and IF that account has receiverSigRequired set, then
        // the transaction
        // must be signed by that account's key. Same if instead it uses a contract as the transfer
        // target.
        if (op.hasTransferAccountID()) {
            context.requireKeyIfReceiverSigRequired(op.transferAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
        } else if (op.hasTransferContractID()) {
            context.requireKeyIfReceiverSigRequired(op.transferContractID(), INVALID_CONTRACT_ID);
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
