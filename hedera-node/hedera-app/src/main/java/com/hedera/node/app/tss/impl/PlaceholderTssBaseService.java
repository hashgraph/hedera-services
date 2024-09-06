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

package com.hedera.node.app.tss.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.tss.TssBaseService;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Placeholder for the TSS base service, added to support testing production of indirect block proofs,
 * c.f. <a href="https://github.com/hashgraph/hedera-services/issues/15379">this issue</a>.
 */
public class PlaceholderTssBaseService implements TssBaseService {
    private static final Logger log = LogManager.getLogger(PlaceholderTssBaseService.class);

    private static final byte[] MOCK_SIGNATURE = new byte[48];

    /**
     * Copy-on-write list to avoid concurrent modification exceptions if a consumer unregisters
     * itself in its callback.
     */
    private final List<BiConsumer<byte[], byte[]>> consumers = new CopyOnWriteArrayList<>();

    private ExecutorService executor;

    @Inject
    public void setExecutor(@NonNull final ExecutorService executor) {
        this.executor = requireNonNull(executor);
    }

    @Override
    public void requestLedgerSignature(@NonNull final byte[] messageHash) {
        requireNonNull(messageHash);
        requireNonNull(executor);
        // Simulate asynchronous completion of the ledger signature
        CompletableFuture.runAsync(
                () -> consumers.forEach(consumer -> {
                    try {
                        consumer.accept(messageHash, MOCK_SIGNATURE);
                    } catch (Exception e) {
                        log.error(
                                "Failed to provide signature {} on message {} to consumer {}",
                                CommonUtils.hex(MOCK_SIGNATURE),
                                CommonUtils.hex(messageHash),
                                consumer,
                                e);
                    }
                }),
                executor);
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
}
