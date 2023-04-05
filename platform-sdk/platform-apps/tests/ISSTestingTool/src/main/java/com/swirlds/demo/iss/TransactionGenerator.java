/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.iss;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.utility.ByteUtils.intToByteArray;

import com.swirlds.common.system.Platform;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.utility.Startable;
import java.util.Random;

/**
 * Generates and submits transactional workload.
 */
public class TransactionGenerator implements Startable {

    private final Random random;
    private final Platform platform;

    private final StoppableThread thread;

    public TransactionGenerator(
            final Random random, final Platform platform, final int networkWideTransactionsPerSecond) {

        this.random = random;
        this.platform = platform;

        // Each node in an N node network should create 1/N transactions per second.
        final int tps =
                networkWideTransactionsPerSecond / platform.getAddressBook().getSize();

        thread = new StoppableThreadConfiguration<>(getStaticThreadManager())
                .setComponent("iss-testing-tool")
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
     * Generate and submit a single transaction.
     */
    private void generateTransaction() {
        // Transactions are simple: take an integer, and add it into the running sum.
        platform.createTransaction(intToByteArray(random.nextInt()));
    }
}
