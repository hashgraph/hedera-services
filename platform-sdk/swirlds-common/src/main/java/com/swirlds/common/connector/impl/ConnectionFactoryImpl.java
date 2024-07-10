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

import com.swirlds.common.connector.Connection;
import com.swirlds.common.connector.ConnectionFactory;
import com.swirlds.common.connector.Input;
import com.swirlds.common.connector.InputWithBackpressure;
import com.swirlds.common.connector.Output;
import com.swirlds.common.connector.Publisher;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

public class ConnectionFactoryImpl implements ConnectionFactory {

    private final ForkJoinPool forkJoinPool = new ForkJoinPool();

    private final static Executor DIRECT_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    public <DATA> Input<DATA> createSyncedInput(Consumer<DATA> inputConsumer) {
        return new DefaultInput<>(inputConsumer, Executors.newSingleThreadExecutor());
    }

    public <DATA> Input<DATA> createAsyncInput(Consumer<DATA> inputConsumer) {
        return new DefaultInput<>(inputConsumer, forkJoinPool);
    }

    @Override
    public <DATA> Input<DATA> createDirectInput(Consumer<DATA> inputConsumer) {
        return new DefaultInput<>(inputConsumer, DIRECT_EXECUTOR);
    }

    public <DATA> InputWithBackpressure<DATA> createSyncedInputWithBackpressure(Consumer<DATA> inputConsumer,
            int capacity) {
        QueueWithBackpressure<DATA> queue = new DefaultQueueWithBackpressure<>(capacity);
        return new DefaultInputWithBackpressure<>(queue, inputConsumer, Executors.newSingleThreadExecutor());
    }

    public <DATA> InputWithBackpressure<DATA> createAsyncInputWithBackpressure(Consumer<DATA> inputConsumer,
            int capacity) {
        QueueWithBackpressure<DATA> queue = new DefaultQueueWithBackpressure<>(capacity);
        return new DefaultInputWithBackpressure<>(queue, inputConsumer, forkJoinPool);
    }

    public <DATA> Publisher<DATA> createPublisher() {
        return new DefaultPublisher<>();
    }

    @Override
    public <DATA> Connection<DATA> connect(Output<DATA> output, Input<DATA> input) {
        return new DefaultConnection<>(output, input.getInputConsumer());
    }

    @Override
    public <DATA> Connection<DATA> connectForOffer(Output<DATA> output, InputWithBackpressure<DATA> input,
            Consumer<DATA> failedOfferHandler) {
        return new DefaultConnection<>(output, input.getInputConsumerForOffer(failedOfferHandler));
    }

    @Override
    public <DATA> Connection<DATA> connectForBypassBackpressure(Output<DATA> output,
            InputWithBackpressure<DATA> input) {
        return new DefaultConnection<>(output, input.getInputConsumerForBypassBackpressure());
    }
}
