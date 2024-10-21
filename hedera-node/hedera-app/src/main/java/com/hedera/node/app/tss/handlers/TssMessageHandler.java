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

import static com.hedera.node.app.tss.TssCryptographyManager.sharesFromWeight;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.roster.ReadableRosterStore;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.tss.TssCryptographyManager;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.pairings.FakeGroupElement;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.pairings.SignatureSchema;
import com.hedera.node.app.tss.stores.WritableTssBaseStore;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    public TssMessageHandler(
            @NonNull final TssSubmissions submissionManager,
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
        // If any of these values are not set it's not a valid input.
        // This message will not be considered to be added to the TSS message store.
        final var tssStore = context.storeFactory().writableStore(WritableTssBaseStore.class);
        final var numberOfAlreadyExistingMessages = tssStore.messageStateSize();

        // The sequence number starts from 0 and increments by 1 for each new message.
        final var key = TssMessageMapKey.newBuilder()
                .rosterHash(op.targetRosterHash())
                .sequenceNumber(numberOfAlreadyExistingMessages)
                .build();
        // Each tss message is stored in the tss message state and is sent to CryptographyManager for further
        // processing.
        tssStore.put(key, op);

        final var tssParticipantDirectory = computeTssParticipantDirectory(context);
        final var result = tssCryptographyManager.handleTssMessageTransaction(op, tssParticipantDirectory, context);

        result.thenAccept(ledgerIdAndSignature -> {
            if (ledgerIdAndSignature != null) {
                // FUTURE: Validate the ledgerId computed is same as the current ledgerId
                final var tssVote = TssVoteTransactionBody.newBuilder()
                        .tssVote(Bytes.wrap(ledgerIdAndSignature.tssVoteBitSet().toByteArray()))
                        .targetRosterHash(op.targetRosterHash())
                        .sourceRosterHash(op.sourceRosterHash())
                        .nodeSignature(ledgerSigner
                                .sign(ledgerIdAndSignature
                                        .ledgerId()
                                        .publicKey()
                                        .toBytes())
                                .getBytes())
                        .ledgerId(Bytes.wrap(
                                ledgerIdAndSignature.ledgerId().publicKey().toBytes()))
                        .build();
                submissionManager.submitTssVote(tssVote, context);
            }
        });
    }

    private TssParticipantDirectory computeTssParticipantDirectory(@NonNull final HandleContext context) {
        final var rosterStore = context.storeFactory().readableStore(ReadableRosterStore.class);
        final var roster = rosterStore.getActiveRoster();
        final var maxSharesPerNode =
                context.configuration().getConfigData(TssConfig.class).maxSharesPerNode();
        final var computedShares = sharesFromWeight(roster.rosterEntries(), maxSharesPerNode);
        final var totalShares =
                computedShares.values().stream().mapToLong(Long::longValue).sum();
        final var threshold = (int) (totalShares + 2) / 2;
        final var builder = TssParticipantDirectory.createBuilder().withThreshold(threshold);
        for (var rosterEntry : roster.rosterEntries()) {
            final int numSharesPerThisNode =
                    computedShares.get(rosterEntry.nodeId()).intValue();
            // FUTURE: Use the actual public key from the node
            final var pairingPublicKey = new PairingPublicKey(
                    new FakeGroupElement(BigInteger.valueOf(10L)), SignatureSchema.create(new byte[] {1}));
            builder.withParticipant((int) rosterEntry.nodeId(), numSharesPerThisNode, pairingPublicKey);
        }
        // FUTURE: Use the actual signature schema
        return builder.build(SignatureSchema.create(new byte[] {1}));
    }
}
