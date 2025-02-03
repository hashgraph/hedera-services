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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.state.WritableLambdaStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.HederaConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class LambdaDispatchHandler implements TransactionHandler {
    @Inject
    public LambdaDispatchHandler() {
        // Dagger2
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var op = context.body().lambdaDispatchOrThrow();
        switch (op.action().kind()) {
            case LAMBDA_ID_TO_DELETE -> {
                final var store = context.storeFactory().writableStore(WritableLambdaStore.class);
                store.markDeleted(op.lambdaIdToDeleteOrThrow());
            }
            case CREATION -> {
                final var store = context.storeFactory().writableStore(WritableLambdaStore.class);
                final var creation = op.creationOrThrow();
                store.installLambda(
                        creation.nextLambdaIndex(),
                        creation.lambdaIdOrThrow(),
                        () -> context.entityNumGenerator().newEntityNum(),
                        context.configuration().getConfigData(HederaConfig.class),
                        creation.installationOrThrow());
            }
            case EXECUTION -> {}
        }
    }
}
