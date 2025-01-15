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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import com.hedera.node.app.blocks.BlockHashSigner;
import com.hedera.node.app.hints.HintsService;
import com.hedera.node.app.history.HistoryService;
import com.hedera.node.app.tss.TssBlockHashSigner;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link BlockHashSigner} that can start ignoring requests for ledger signatures when requested.
 */
public class LapsingBlockHashSigner implements BlockHashSigner {
    private final BlockHashSigner delegate;

    private boolean ignoreRequests = false;

    public LapsingBlockHashSigner(
            @NonNull final HintsService hintsService,
            @NonNull final HistoryService historyService,
            @NonNull final ConfigProvider configProvider) {
        this.delegate = new TssBlockHashSigner(hintsService, historyService, configProvider);
    }

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
    public boolean isReady() {
        return delegate.isReady();
    }

    @Override
    public CompletableFuture<Bytes> signFuture(@NonNull final Bytes blockHash) {
        return ignoreRequests ? new CompletableFuture<>() : delegate.signFuture(blockHash);
    }
}
