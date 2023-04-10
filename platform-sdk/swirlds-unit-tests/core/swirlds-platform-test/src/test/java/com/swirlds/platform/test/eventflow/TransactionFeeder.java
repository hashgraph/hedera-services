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

package com.swirlds.platform.test.eventflow;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import com.swirlds.common.test.TransactionUtils;
import com.swirlds.common.threading.framework.Stoppable;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import java.time.Duration;
import java.util.Arrays;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Feeds transactions to a consumer in a worker thread.
 */
public class TransactionFeeder {
    /** The average number of transactions per event. */
    private static final double TX_PER_EVENT_AVG = 3;

    /** The standard deviation for the number of transactions per event. */
    private static final double TX_PER_EVENT_STD_DEV = 3;

    private final StoppableThread worker;
    private final Random random;
    private final Consumer<ConsensusTransactionImpl> transactionConsumer;
    private final Duration timeBetweenSubmissions;
    private int numSystemTransactions = 0;

    public TransactionFeeder(
            final Random random,
            final long selfId,
            final Consumer<ConsensusTransactionImpl> transactionConsumer,
            final Duration timeBetweenSubmissions) {
        this.random = random;
        this.transactionConsumer = transactionConsumer;
        this.timeBetweenSubmissions = timeBetweenSubmissions;
        worker = getStaticThreadManager().newStoppableThreadConfiguration()
                .setNodeId(selfId)
                .setThreadName("transaction-submitter")
                .setWork(this::feedTransactions)
                .setStopBehavior(Stoppable.StopBehavior.INTERRUPTABLE)
                .build();
    }

    public void start() {
        worker.start();
    }

    public void stop() {
        worker.stop();
    }

    public int getNumSystemTransactions() {
        return numSystemTransactions;
    }

    /**
     * Sleep for a period of time, then feed some transactions to the consumer.
     */
    private void feedTransactions() {
        try {
            MILLISECONDS.sleep(timeBetweenSubmissions.toMillis());
        } catch (final InterruptedException e) {
            // ignored
        }
        final ConsensusTransactionImpl[] txns =
                TransactionUtils.incrementingMixedTransactions(random, TX_PER_EVENT_AVG, TX_PER_EVENT_STD_DEV, 0.5);
        Arrays.stream(txns).forEach(tx -> {
            transactionConsumer.accept(tx);
            if (tx.isSystem()) {
                numSystemTransactions++;
            }
        });
    }
}
