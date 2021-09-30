package com.hedera.services.disruptor;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Base class for transaction dispatch layers that use an LMAX disruptor to coordinate the
 * execution sequence of the various stages of the transaction lifecycle. This class is
 * responsible for creation of the ring buffer. Assignment of the event handlers to the
 * ring buffer is governed by a consumer lambda in the constructor.
 *
 * The ring-buffer that backs the disruptor contains references to transactions as they are
 * received by the ServicesState application layer. The ring buffer MUST have a size that is
 * a power of 2 as the disruptor layer relies on this property for fast modulo calculations.
 *
 * The wait strategy employed by the disruptor has a direct effect on its performance but the
 * choice must be balanced by the number of dedicated CPU cores available to the application
 * process. Below are our findings for various wait strategies tested via JMH.
 *
 * PhasedBackoffWaitStrategy
 * 1 handler (events/s) -> 38.44M ± 2.96M
 * 2 handlers           -> 9.34M ± 2.69M
 * 3 handlers           -> 7.62M ± 1.69M
 * 4 handlers           -> 9.24M ± 4.62M
 * 10 handlers          -> 7.47M ± 5.8M
 *
 * BlockingWaitStrategy
 *  1 handler (events/s) -> 38.9M ± 2.02M
 *  2 handlers           -> 12.33M ± 2.14M
 *  3 handlers           -> 8.79M ± 2.51M
 *  4 handlers           -> 6M ± 1.54M
 *  10 handlers          -> 3.05M ± 0.53M
 *
 * BusySpinWaitStrategy
 *  1 handler (events/s) -> 35.59M ± 0.27M
 *  2 handlers           -> 33.19M ± 4.72M
 *  3 handlers           -> 30.72M ± 2.2M
 *  4 handlers           -> 28.15M ± 4.55M
 *  10 handlers          -> 23.89M ± 5.64M
 *
 * YieldingWaitStrategy
 *  1 handler (events/s) -> 35.83M ± 0.62M
 *  2 handlers           -> 28.5M ± 2.33M
 *  3 handlers           -> 26.2M ± 2.92M
 *  4 handlers           -> 25.7M ± 3M
 *  10 handlers          -> 23.02M ± 5.44M
 *
 * We selected YieldingWaitStrategy due to its resilience with large numbers of handlers. Each handler
 * will consume CPU vigorously if allowed but will yield to other tasks (via Thread.yield) if the
 * scheduler elects to do so.
 */
public abstract class AbstractProcessor {
    public static final int DEFAULT_BUFFER_POWER = 14;  // 2^14

    protected Disruptor<TransactionEvent> disruptor;

    protected AbstractProcessor(
            int bufferPower,
            String threadPrefix,
            Consumer<Disruptor<TransactionEvent>> handlerConstructor
    ) {
        if (bufferPower <= 0)
            bufferPower = DEFAULT_BUFFER_POWER;

        // Ring buffer size MUST be a power of 2 due to modulo bit operations done at disruptor layer.
        final int bufferSize = (int) Math.pow(2, bufferPower);

        AtomicInteger i = new AtomicInteger();
        disruptor = new Disruptor<>(
                TransactionEvent::new,
                bufferSize,
                (Runnable r) -> {
                    Thread t = new Thread(r);
                    t.setName(threadPrefix + i.getAndAdd(1));
                    t.setDaemon(true);
                    return t;
                },
                ProducerType.SINGLE,
                new YieldingWaitStrategy());

        handlerConstructor.accept(disruptor);
        disruptor.start();
    }

    public void shutdown() { disruptor.shutdown(); }
}
