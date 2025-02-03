/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.LambdaOwnerID.OwnerIdOneOfType.ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_LAMBDA_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.LAMBDA_STORAGE_KEY_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.LAMBDA_STORAGE_VALUE_TOO_LONG;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.state.WritableLambdaStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LambdaSStoreHandler implements TransactionHandler {
    private static final long MAX_KV_LEN = 32L;

    @Inject
    public LambdaSStoreHandler() {
        // Dagger2
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.lambdaSstoreOrThrow();
        validateTruePreCheck(op.hasLambdaId(), INVALID_LAMBDA_ID);
        final var lambdaId = op.lambdaIdOrThrow();
        validateTruePreCheck(lambdaId.hasOwnerId(), INVALID_LAMBDA_ID);
        final var ownerType = lambdaId.ownerIdOrThrow().ownerId().kind();
        validateTruePreCheck(ownerType == ACCOUNT_ID, INVALID_LAMBDA_ID);
        // Lambda indexes start at 1
        validateTruePreCheck(lambdaId.index() > 0, INVALID_LAMBDA_ID);
        for (final var slot : op.storageSlots()) {
            validateTruePreCheck(slot.key().length() <= MAX_KV_LEN, LAMBDA_STORAGE_KEY_TOO_LONG);
            validateTruePreCheck(slot.value().length() <= MAX_KV_LEN, LAMBDA_STORAGE_VALUE_TOO_LONG);
        }
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().lambdaSstoreOrThrow();
        context.requireKeyOrThrow(op.lambdaIdOrThrow().ownerIdOrThrow().accountIdOrThrow(), INVALID_LAMBDA_ID);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var op = context.body().lambdaSstoreOrThrow();
        final var lambdaStore = context.storeFactory().writableStore(WritableLambdaStore.class);
        lambdaStore.updateSlots(op.lambdaIdOrThrow(), op.storageSlots());
    }
}
