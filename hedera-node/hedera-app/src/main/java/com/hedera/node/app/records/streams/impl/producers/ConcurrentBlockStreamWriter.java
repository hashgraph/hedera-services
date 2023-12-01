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

package com.hedera.node.app.records.streams.impl.producers;

import com.hedera.hapi.streams.HashObject;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ConcurrentBlockStreamWriter is a wrapper around a BlockStreamWriter that allows for concurrent writes to multiple
 * writers. The write methods do not block so that the caller may continue to make progress.
 */
public class ConcurrentBlockStreamWriter implements BlockStreamWriter {
    private final BlockStreamWriter writer;
    private final ExecutorService executorService;
    private final AtomicReference<CompletableFuture<Void>> lastFutureRef;

    public ConcurrentBlockStreamWriter(ExecutorService executorService, BlockStreamWriter writer) {
        this.executorService = executorService;
        this.writer = writer;
        this.lastFutureRef = new AtomicReference<>(CompletableFuture.completedFuture(null));
    }

    @Override
    public void init(long blockNumber) {
        updateLastFuture(CompletableFuture.runAsync(() -> writer.init(blockNumber), executorService));
    }

    @Override
    public void writeItem(@NonNull Bytes item) {
        updateLastFuture(CompletableFuture.runAsync(() -> writer.writeItem(item), executorService));
    }

    @Override
    public void close(@NonNull HashObject endRunningHash) {
        updateLastFuture(CompletableFuture.runAsync(() -> writer.close(endRunningHash), executorService));
    }

    private void updateLastFuture(CompletableFuture<Void> updater) {
        lastFutureRef.updateAndGet(lastFuture -> lastFuture.thenCompose(v -> updater));
    }
}
