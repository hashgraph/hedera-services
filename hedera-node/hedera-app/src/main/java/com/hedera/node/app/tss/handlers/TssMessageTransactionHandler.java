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

package com.hedera.node.app.tss.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.tss.TssCryptographyManager;
import com.hedera.node.app.tss.stores.WritableTssBaseStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TssMessageTransactionHandler implements TransactionHandler {
    private static final Logger log = LogManager.getLogger(TssMessageTransactionHandler.class);
    private final TssCryptographyManager tssCryptographyManager;

    public TssMessageTransactionHandler(TssCryptographyManager tssCryptographyManager) {
        this.tssCryptographyManager = tssCryptographyManager;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
       // No-op
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        // No-op
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var op = context.body().tssMessageOrThrow();
        // If any of these values are not set it is not a valid input.
        // This message will not be considered to be added to the TSS message store.
        // Are there any other checks that need to be done?
        if (op.targetRosterHash().toByteArray().length == 0
                || op.sourceRosterHash().toByteArray().length == 0
                || op.shareIndex() < 0
                || op.tssMessage().toByteArray().length == 0) {
            log.warn("Invalid TSS message transaction. Not adding to the TSS message store.");
            return;
        }

        final var tssStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var numberOfAlreadyExistingMessages = tssStore.messageStateSize();
        final var key = TssMessageMapKey.newBuilder()
                .rosterHash(op.targetRosterHash())
                .sequenceNumber(numberOfAlreadyExistingMessages + 1)
                .build();
        // if the tss message already exists in the store, do not add it again.
        if(tssStore.getMessage(key) != null){
            log.warn("TSS message already exists in the store. Not adding to the TSS message store.");
            return;
        }
        tssStore.put(key, op);
        // Once a TSS message is added to the store, it is sent to the TSS cryptography manager.
        tssCryptographyManager.handleTssMessageTransaction(op);
    }
}
