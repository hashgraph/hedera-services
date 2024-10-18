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
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.tss.TssCryptographyManager;
import com.hedera.node.app.tss.stores.WritableTssBaseStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Validates and potentially responds with a vote to a {@link TssMessageTransactionBody}.
 * (TSS-FUTURE) Tracked <a href="https://github.com/hashgraph/hedera-services/issues/14749">here</a>.
 */
@Singleton
public class TssMessageHandler implements TransactionHandler {
    private static final Logger log = LogManager.getLogger(TssMessageHandler.class);
    private final TssSubmissions submissionManager;
    private final AppContext.LedgerSigner ledgerSigner;
    private final TssCryptographyManager tssCryptographyManager;

    @Inject
    public TssMessageHandler(@NonNull final TssSubmissions submissionManager,
                             @NonNull final AppContext.LedgerSigner ledgerSigner,
                             @NonNull final TssCryptographyManager tssCryptographyManager) {
        this.submissionManager = requireNonNull(submissionManager);
        this.ledgerSigner = requireNonNull(ledgerSigner);
        this.tssCryptographyManager = requireNonNull(tssCryptographyManager);
    }

    @Override
    public void preHandle(@NonNull PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var op = context.body().tssMessageOrThrow();
        // If any of these values are not set its not a valid input. This message will not be considered to be added to
        // the
        // TSS message store.
        if (op.targetRosterHash().toByteArray().length == 0
                || op.sourceRosterHash().toByteArray().length == 0
                || op.shareIndex() < 0
                || op.tssMessage().toByteArray().length == 0) {
            log.warn("Invalid TSS message transaction. Not adding to the TSS message store.");
            return;
        }

        final var tssState = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var numberOfAlreadyExistingMessages = tssState.messageStateSize();
        // The sequence number starts from 0 and increments by 1 for each new message.
        final var key = TssMessageMapKey.newBuilder()
                .rosterHash(op.targetRosterHash())
                .sequenceNumber(numberOfAlreadyExistingMessages)
                .build();
        // Each tss message is stored in the tss message state and is sent to CryptographyManager for further processing.
        tssState.put(key, op);
        tssCryptographyManager.handleTssMessageTransaction(op, context);

        final var tssVote = TssVoteTransactionBody.newBuilder()
                .tssVote(op.targetRosterHash())
                .targetRosterHash(op.targetRosterHash())
                .sourceRosterHash(op.sourceRosterHash())
                .nodeSignature(ledgerSigner.sign(Bytes.EMPTY).getBytes())
                .ledgerId(Bytes.EMPTY)
                .build();
        submissionManager.submitTssVote(tssVote, context);
    }
}
