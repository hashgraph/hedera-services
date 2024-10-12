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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.tss.TssBaseService;
import com.hedera.node.app.tss.handlers.TssHandlers;
import com.hedera.services.bdd.junit.HapiTest;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A fake implementation of the {@link TssBaseService} that,
 * <ul>
 *     <Li>Lets the author of an embedded {@link HapiTest} control whether the TSS base service ignores
 *     signature requests; and, when requests are <i>not</i> ignored,</Li>
 *     <li>"Signs" messages by scheduling callback to its consumers using the SHA-384 hash of the
 *     message as the signature.</li>
 * </ul>
 */
public class FakeTssBaseService implements TssBaseService {
    private static final Logger log = LogManager.getLogger(FakeTssBaseService.class);

    /**
     * Copy-on-write list to avoid concurrent modification exceptions if a consumer unregisters
     * itself in its callback.
     */
    private final List<BiConsumer<byte[], byte[]>> consumers = new CopyOnWriteArrayList<>();

    private boolean ignoreRequests = false;

    /**
     * When called, will start ignoring any requests for ledger signatures.
     */
    public void startIgnoringRequests() {
        ignoreRequests = true;
    }

    /**
     * When called, will stop ignoring any requests for ledger signatures.
     */
    public void stopIgnoringRequests() {
        ignoreRequests = false;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        // No-op for now
    }

    @Override
    public void requestLedgerSignature(@NonNull final byte[] messageHash) {
        requireNonNull(messageHash);
        if (ignoreRequests) {
            return;
        }
        final var mockSignature = noThrowSha384HashOf(messageHash);
        // Simulate asynchronous completion of the ledger signature
        CompletableFuture.runAsync(() -> consumers.forEach(consumer -> {
            try {
                consumer.accept(messageHash, mockSignature);
            } catch (Exception e) {
                log.error(
                        "Failed to provide signature {} on message {} to consumer {}",
                        CommonUtils.hex(mockSignature),
                        CommonUtils.hex(messageHash),
                        consumer,
                        e);
            }
        }));
    }

    @Override
    public void registerLedgerSignatureConsumer(@NonNull final BiConsumer<byte[], byte[]> consumer) {
        requireNonNull(consumer);
        consumers.add(consumer);
    }

    @Override
    public void unregisterLedgerSignatureConsumer(@NonNull final BiConsumer<byte[], byte[]> consumer) {
        requireNonNull(consumer);
        consumers.remove(consumer);
    }

    @Override
    public TssHandlers tssHandlers() {
        return new TssHandlers(
                NoopTransactionHandler.NOOP_TRANSACTION_HANDLER, NoopTransactionHandler.NOOP_TRANSACTION_HANDLER);
    }

    private enum NoopTransactionHandler implements TransactionHandler {
        NOOP_TRANSACTION_HANDLER;

        @Override
        public void preHandle(@NonNull final PreHandleContext context) {
            // No-op
        }

        @Override
        public void pureChecks(@NonNull TransactionBody txn) {
            // No-op
        }

        @Override
        public void handle(@NonNull HandleContext context) throws HandleException {
            // No-op
        }
    }
}
