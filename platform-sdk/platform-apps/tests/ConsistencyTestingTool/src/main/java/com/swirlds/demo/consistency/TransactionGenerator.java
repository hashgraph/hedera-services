/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.consistency;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.utility.ByteUtils.longToByteArray;

import com.swirlds.base.state.Startable;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.platform.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Random;

/**
 * Generates and submits transactional workload
 */
public class TransactionGenerator implements Startable {
    /**
     * A source of randomness
     */
    private final Random random;

    /**
     * The platform to submit transactions to
     */
    private final Platform platform;

    /**
     * The thread that generates and submits transactions
     */
    private final StoppableThread thread;

    /**
     * Constructor
     *
     * @param random                           a source of randomness
     * @param platform                         the platform to submit transactions to
     * @param networkWideTransactionsPerSecond the number of transactions to generate per second, network-wide
     */
    public TransactionGenerator(
            @NonNull final Random random,
            @NonNull final Platform platform,
            final int networkWideTransactionsPerSecond) {

        this.random = Objects.requireNonNull(random);
        this.platform = Objects.requireNonNull(platform);

        // Each node in an N node network should create 1/N transactions per second.
        final double tps = (double) networkWideTransactionsPerSecond
                / platform.getRoster().rosterEntries().size();

        thread = new StoppableThreadConfiguration<>(getStaticThreadManager())
                .setComponent("consistency-testing-tool")
                .setThreadName("transaction-generator")
                .setMaximumRate(tps)
                .setWork(this::generateTransaction)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        thread.start();
    }

    /**
     * Generate and submit a single transaction
     * <p>
     * Each transaction consists of a single random long value
     */
    private void generateTransaction() {
        platform.createTransaction(longToByteArray(random.nextLong()));
    }
}
