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

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
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
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
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
    // From AppContext
    private final InstantSource instantSource;
    private final SortedSet<SignatureRequest> requests = new TreeSet<>();
    private final Map<Bytes, Set<TssShareSignature>> signatures = new ConcurrentHashMap<>();
    private Instant lastPurgeTime = Instant.EPOCH;

    @Inject
    public TssShareSignatureHandler(final TssLibrary tssLibrary, final InstantSource instantSource) {
        this.tssLibrary = tssLibrary;
        this.instantSource = instantSource;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var body = context.body().tssShareSignatureOrThrow();
        final var shareSignature = body.shareSignature();
        final var messageHash = body.messageHash();
        final var shareIndex = body.shareIndex();
        final var rosterHash = body.rosterHash();

        // Extract the bytes B being signed
        // computeIfAbsent() the set of signatures on B
        // For each signature on B not already present, verify with tssLibrary and accumulate in map
        // If B now has sufficient signatures to aggregate, do so and notify tssBaseService of sig on B
        signatures.computeIfAbsent(messageHash, k -> ConcurrentHashMap.newKeySet());

        // Step 2: Verify the signature using TSS library
        final var tssShareSignature = new TssShareSignature(
                new TssShareId((int) shareIndex),
                new PairingSignature(
                        new FakeGroupElement(BigInteger.valueOf(shareIndex)),
                        SignatureSchema.create(shareSignature.toByteArray())));
        //        if (!tssLibrary.verifySignature(tssShareSignature)) {
        //            throw new PreCheckException("Invalid TSS share signature");
        //        }

        // Purge any expired signature requests, at most once per second
        final var now = instantSource.instant();
        if (now.getEpochSecond() > lastPurgeTime.getEpochSecond()) {
            synchronized (requests) {
                requests.removeIf(req -> req.timestamp().isBefore(now.minusSeconds(60)));
            }
            lastPurgeTime = now;
        }
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
