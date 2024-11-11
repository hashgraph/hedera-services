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

import static com.hedera.node.app.tss.handlers.TssUtils.getThresholdForTssMessages;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.TssBaseServiceImpl;
import com.hedera.node.app.tss.TssKeyMaterialAccessor;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssShareId;
import com.hedera.node.app.tss.api.TssShareSignature;
import com.hedera.node.app.tss.pairings.FakeGroupElement;
import com.hedera.node.app.tss.pairings.PairingSignature;
import com.hedera.node.app.tss.pairings.SignatureSchema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Handles TSS share signature transactions.
 * This is yet to be implemented.
 */
@Singleton
public class TssShareSignatureHandler implements TransactionHandler {
    private final TssLibrary tssLibrary;
    private final TssKeyMaterialAccessor rosterKeyMaterialAccessor;
    private final InstantSource instantSource;
    private final Map<Bytes, Instant> requests = new ConcurrentHashMap<>();
    private final Map<Bytes, Map<Bytes, Set<TssShareSignature>>> signatures = new ConcurrentHashMap<>();
    private Instant lastPurgeTime = Instant.EPOCH;
    private TssBaseServiceImpl tssBaseService;

    @Inject
    public TssShareSignatureHandler(
            @NonNull final TssLibrary tssLibrary,
            @NonNull final InstantSource instantSource,
            @NonNull final TssKeyMaterialAccessor rosterKeyMaterialAccessor,
            @NonNull final TssBaseService tssBaseService) {
        this.tssLibrary = tssLibrary;
        this.instantSource = instantSource;
        this.rosterKeyMaterialAccessor = rosterKeyMaterialAccessor;
        this.tssBaseService = (TssBaseServiceImpl) tssBaseService;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var body = context.body().tssShareSignatureOrThrow();
        final var shareSignature = body.shareSignature();
        final var messageHash = body.messageHash();
        final var shareIndex = body.shareIndex();
        final var rosterHash = body.rosterHash();

        // verify if the signature is already present
        final var isPresent = signatures.containsKey(messageHash)
                && signatures.get(messageHash).containsKey(rosterHash)
                && signatures.get(messageHash).get(rosterHash).stream()
                        .anyMatch(sig -> sig.shareId().idElement() == shareIndex);

        // For each signature not already present for this message hash, verify with
        // tssLibrary and accumulate in map
        if (!isPresent) {
            validateAndAccumulateSignatures(shareSignature, messageHash, rosterHash, shareIndex);
        }
        // If message hash now has enough signatures to aggregate, do so and notify
        // tssBaseService of sign the message hash with ledger signature
        if (isThresholdMet(messageHash, rosterHash)) {
            final var tssShareSignatures = this.signatures.get(messageHash).get(rosterHash);
            final var ledgerSignature =
                    tssLibrary.aggregateSignatures(tssShareSignatures.stream().toList());
            tssBaseService.notifySignature(
                    messageHash.toByteArray(), ledgerSignature.signature().toBytes());
        }

        // Purge any expired signature requests, at most once per second
        final var now = instantSource.instant();
        if (now.getEpochSecond() > lastPurgeTime.getEpochSecond()) {
            lastPurgeTime = now;
            Instant threshold = now.minusSeconds(60);
            requests.entrySet().removeIf(entry -> threshold.isAfter(entry.getValue()));
        }
    }

    private void validateAndAccumulateSignatures(
            final Bytes shareSignature, final Bytes messageHash, final Bytes rosterHash, final long shareIndex) {
        final var tssShareSignature = new TssShareSignature(
                new TssShareId((int) shareIndex),
                new PairingSignature(
                        new FakeGroupElement(BigInteger.valueOf(shareIndex)),
                        SignatureSchema.create(shareSignature.toByteArray())));
        final var isValid = tssLibrary.verifySignature(
                rosterKeyMaterialAccessor.activeRosterParticipantDirectory(),
                rosterKeyMaterialAccessor.activeRosterPublicShares(),
                tssShareSignature);
        if (isValid) {
            signatures
                    .computeIfAbsent(messageHash, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(rosterHash, k -> ConcurrentHashMap.newKeySet())
                    .add(tssShareSignature);
            requests.computeIfAbsent(messageHash, k -> instantSource.instant());
        }
    }

    private boolean isThresholdMet(final Bytes messageHash, final Bytes rosterHash) {
        final var shares = signatures.get(messageHash);
        final var getAllSignatures = shares != null ? shares.get(rosterHash) : Set.of();
        final var totalShares =
                rosterKeyMaterialAccessor.activeRosterParticipantDirectory().getSharesById().values().stream()
                        .mapToLong(List::size)
                        .sum();
        return getAllSignatures.size() >= getThresholdForTssMessages(totalShares);
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
    }

    private record SignatureRequest(Bytes message, Instant timestamp) implements Comparable<SignatureRequest> {
        @Override
        public int compareTo(@NonNull final SignatureRequest o) {
            return timestamp.compareTo(o.timestamp());
        }
    }
}
