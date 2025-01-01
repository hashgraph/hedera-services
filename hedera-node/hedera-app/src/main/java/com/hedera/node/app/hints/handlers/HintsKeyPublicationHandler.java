/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hints.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hints.ReadableHintsStore.HintsKeyPublication;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.hints.impl.HintsConstructionControllers;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HintsKeyPublicationHandler implements TransactionHandler {
    private final HintsConstructionControllers controllers;

    @Inject
    public HintsKeyPublicationHandler(@NonNull final HintsConstructionControllers controllers) {
        this.controllers = requireNonNull(controllers);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody body) throws PreCheckException {
        requireNonNull(body);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var op = context.body().hintsKeyPublicationOrThrow();
        controllers.getInProgressByUniverseSizeLog2(op.maxSizeLog2()).ifPresent(controller -> {
            final long nodeId = context.creatorInfo().nodeId();
            final var partyId = controller.partyIdOf(nodeId).orElseGet(controller::nextPartyId);
            final var hintsKey = op.hintsKeyOrThrow();
            final var hintsStore = context.storeFactory().writableStore(WritableHintsStore.class);
            final var adoptionTime = context.consensusNow();
            if (hintsStore.includeHintsKey(op.maxSizeLog2(), partyId, nodeId, hintsKey, adoptionTime)) {
                controller.incorporateHintsKey(new HintsKeyPublication(hintsKey, nodeId, partyId, adoptionTime));
            }
        });
    }
}
