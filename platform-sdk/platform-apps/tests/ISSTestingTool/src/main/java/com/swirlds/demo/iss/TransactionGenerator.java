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

package com.swirlds.demo.iss;

import static com.swirlds.common.test.fixtures.RandomUtils.nextLong;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.utility.ByteUtils.intToByteArray;
import static com.swirlds.common.utility.ByteUtils.longToByteArray;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.state.Startable;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SwirldState;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Generates and submits transactional workload.
 */
public class TransactionGenerator implements Startable {

    private final Random random;
    private final Platform platform;
    private final ISSTestingToolState issTestingToolState;

    private final StoppableThread thread;
    private final StoppableThread systemTransactionsThread;

    public TransactionGenerator(
            final Random random, final Platform platform, final int networkWideTransactionsPerSecond, final SwirldState issTestingToolState) {

        this.random = random;
        this.platform = platform;
        this.issTestingToolState = (ISSTestingToolState) issTestingToolState;

        // Each node in an N node network should create 1/N transactions per second.
        final int tps = networkWideTransactionsPerSecond
                / platform.getRoster().rosterEntries().size();

        thread = new StoppableThreadConfiguration<>(getStaticThreadManager())
                .setComponent("iss-testing-tool")
                .setThreadName("transaction-generator")
                .setMaximumRate(tps)
                .setWork(this::generateTransaction)
                .build();

        systemTransactionsThread = new StoppableThreadConfiguration<>(getStaticThreadManager())
                .setComponent("iss-testing-tool")
                .setThreadName("system-transaction-generator")
                .setMaximumRate(tps)
                .setWork(this::generateSystemTransaction)
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        thread.start();
        systemTransactionsThread.start();
    }

    /**
     * Generate and submit a single transaction.
     */
    private void generateTransaction() {
        // Transactions are simple: take an integer, and add it into the running sum.
        platform.createTransaction(intToByteArray(random.nextInt()));
    }

    /**
     * Generates and submits a system transaction.
     * This method creates a StateSignatureTransaction with a random round number,
     * signature, and hash, encodes it, and submits it to the platform.
     */
    private void generateSystemTransaction() {
        final long round = nextLong();
        final byte[] signature = new byte[384];
        random.nextBytes(signature);
        final byte[] hash = new byte[48];
        random.nextBytes(hash);
        final var stateSignatureTransaction = StateSignatureTransaction.newBuilder().signature(Bytes.wrap(signature)).hash(Bytes.wrap(hash)).round(round).build();

        final var encodedStateSignatureTransaction = issTestingToolState.encodeSystemTransaction(stateSignatureTransaction);

        platform.createTransaction(encodedStateSignatureTransaction.toByteArray());
    }
}
