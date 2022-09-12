/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stats;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hederahashgraph.api.proto.java.TransactionID;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ExecutionTimeTracker {
    private static final Logger log = LogManager.getLogger(ExecutionTimeTracker.class);

    private final boolean shouldNoop;
    private final TransactionContext txnCtx;

    private final Cache<TransactionID, Long> execNanosCache;

    private long startTime;

    @Inject
    public ExecutionTimeTracker(TransactionContext txnCtx, NodeLocalProperties properties) {
        this.txnCtx = txnCtx;

        final var timesToTrack = properties.numExecutionTimesToTrack();
        shouldNoop = (timesToTrack == 0);
        if (shouldNoop) {
            execNanosCache = null;
            log.info("Not tracking execution times (stats.executionTimesToTrack=0)");
        } else {
            execNanosCache = CacheBuilder.newBuilder().maximumSize(timesToTrack).build();
            log.info("Tracking last {} execution times", timesToTrack);
        }
    }

    public void start() {
        if (shouldNoop) {
            return;
        }
        startTime = System.nanoTime();
    }

    public void stop() {
        if (shouldNoop) {
            return;
        }
        final var execTime = System.nanoTime() - startTime;
        final var txnId = txnCtx.accessor().getTxnId();
        execNanosCache.put(txnId, execTime);
    }

    public Long getExecNanosIfPresentFor(TransactionID txnId) {
        return shouldNoop ? null : execNanosCache.getIfPresent(txnId);
    }

    /* --- Only used by unit tests --- */
    boolean isShouldNoop() {
        return shouldNoop;
    }

    Cache<TransactionID, Long> getExecNanosCache() {
        return execNanosCache;
    }
}
