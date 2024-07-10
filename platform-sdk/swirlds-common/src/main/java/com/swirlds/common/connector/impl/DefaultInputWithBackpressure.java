/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.common.connector.impl;

import com.swirlds.common.connector.InputWithBackpressure;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class DefaultInputWithBackpressure<DATA> implements InputWithBackpressure<DATA> {

    private final QueueWithBackpressure<DATA> queue;

    private final Consumer<DATA> inputConsumer;

    public DefaultInputWithBackpressure(QueueWithBackpressure<DATA> queue, Consumer<DATA> inputConsumer,
            Executor executor) {
        this.queue = queue;
        this.inputConsumer = inputConsumer;
        executor.execute(() -> handle());
    }

    private void handle() {
        while (true) {
            try {
                DATA data = queue.take();
                inputConsumer.accept(data);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupt in Wiring Framework", e);
            }
        }
    }

    @Override
    public Consumer<DATA> getInputConsumer() {
        return data -> {
            try {
                queue.put(data);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupt in Wiring Framework", e);
            }
        };
    }

    @Override
    public Consumer<DATA> getInputConsumerForOffer(Consumer<DATA> failedOfferHandler) {
        return data -> {
            final boolean accepted = queue.offer(data);
            if (!accepted) {
                if (failedOfferHandler != null) {
                    failedOfferHandler.accept(data);
                }
            }
        };
    }

    @Override
    public Consumer<DATA> getInputConsumerForBypassBackpressure() {
        return data -> {
            queue.bypassBackpressure(data);
        };
    }

}
