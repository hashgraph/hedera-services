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

import com.hedera.services.ServicesState;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
 * <table>
 *   <caption>Wait strategy benchmark results</caption>
 *   <tr><th>Strategy</th><th>Test</th><th>Result (events/s)</th></tr>
 *   <tr><td>PhasedBackoffWaitStrategy</td><td></td><td></td></tr>
 *   <tr><td></td><td>1 handler</td><td>38.44M ± 2.96M</td></tr>
 *   <tr><td></td><td>2 handlers</td><td>9.34M ± 2.69M</td></tr>
 *   <tr><td></td><td>3 handlers</td><td>7.62M ± 1.69M</td></tr>
 *   <tr><td></td><td>4 handlers</td><td>9.24M ± 4.62M</td></tr>
 *   <tr><td></td><td>10 handlers</td><td>7.47M ± 5.8M</td></tr>
 *   <tr><td>BlockingWaitStrategy</td><td></td><td></td></tr>
 *   <tr><td></td><td>1 handler</td><td>38.9M ± 2.02M</td></tr>
 *   <tr><td></td><td>2 handlers</td><td>12.33M ± 2.14M</td></tr>
 *   <tr><td></td><td>3 handlers</td><td>8.79M ± 2.51M</td></tr>
 *   <tr><td></td><td>4 handlers</td><td>6M ± 1.54M</td></tr>
 *   <tr><td></td><td>10 handlers</td><td>3.05M ± 0.53M</td></tr>
 *   <tr><td>BusySpinWaitStrategy</td><td></td><td></td></tr>
 *   <tr><td></td><td>1 handler</td><td>35.59M ± 0.27M</td></tr>
 *   <tr><td></td><td>2 handlers</td><td>33.19M ± 4.72M</td></tr>
 *   <tr><td></td><td>3 handlers</td><td>30.72M ± 2.2M</td></tr>
 *   <tr><td></td><td>4 handlers</td><td>28.15M ± 4.55M</td></tr>
 *   <tr><td></td><td>10 handlers</td><td>23.89M ± 5.64M</td></tr>
 *   <tr><td>YieldingWaitStrategy</td><td></td><td></td></tr>
 *   <tr><td></td><td>1 handler</td><td>35.83M ± 0.62M</td></tr>
 *   <tr><td></td><td>2 handlers</td><td>28.5M ± 2.33M</td></tr>
 *   <tr><td></td><td>3 handlers</td><td>26.2M ± 2.92M</td></tr>
 *   <tr><td></td><td>4 handlers</td><td>25.7M ± 3M</td></tr>
 *   <tr><td></td><td>10 handlers</td><td>23.02M ± 5.44M</td></tr>
 * </table>
 *
 * We selected YieldingWaitStrategy due to its resilience with large numbers of handlers. Each handler
 * will consume CPU vigorously if allowed but will yield to other tasks (via {@code }Thread.yield}) if the
 * scheduler elects to do so.
 */
public abstract class AbstractProcessor {
    private static final Logger logger = LogManager.getLogger(ServicesState.class);

    public static final int DEFAULT_BUFFER_POWER = 14;  // 2^14

    protected Disruptor<TransactionEvent> disruptor;

    protected AbstractProcessor(
            int bufferPower,
            String threadPrefix,
            Consumer<Disruptor<TransactionEvent>> handlerConstructor
    ) {
        logger.warn("creating processor");
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
