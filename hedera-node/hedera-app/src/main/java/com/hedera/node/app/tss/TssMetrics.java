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

package com.hedera.node.app.tss;

import static com.hedera.hapi.node.base.HederaFunctionality.TSS_MESSAGE;
import static com.hedera.hapi.node.base.HederaFunctionality.TSS_VOTE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.swirlds.metrics.api.Counter;
import com.swirlds.metrics.api.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TssMetrics {

    private static final Counter.Config TSS_MESSAGE_TX_COUNT =
            new Counter.Config("app", "tss_message_total").withDescription("total number of tss message transactions");

    private static final Counter.Config TSS_VOTE_TX_COUNT =
            new Counter.Config("app", "tss_vote_total").withDescription("total number of tss vote transactions");

    private final Counter tssMessageTxCount;
    private final Counter tssVoteTxCount;

    /**
     * Constructor for the TssMetrics
     *
     * @param metrics the {@link Metrics} object where all metrics will be registered
     */
    @Inject
    public TssMetrics(@NonNull final Metrics metrics) {
        requireNonNull(metrics, "metrics must not be null");

        tssMessageTxCount = metrics.getOrCreate(TSS_MESSAGE_TX_COUNT);
        tssVoteTxCount = metrics.getOrCreate(TSS_VOTE_TX_COUNT);
    }

    /**
     * Increment counter metrics for TssVote or TssMessage transactions.
     *
     * @param functionality the TSS {@link HederaFunctionality} for which the metrics will be updated
     */
    public void updateTssMetrics(@NonNull final HederaFunctionality functionality) {
        requireNonNull(functionality, "functionality must not be null");
        if (functionality == HederaFunctionality.NONE) {
            return;
        }

        if (functionality == TSS_MESSAGE) {
            tssMessageTxCount.increment();
        } else if (functionality == TSS_VOTE) {
            tssVoteTxCount.increment();
        }
    }
}
