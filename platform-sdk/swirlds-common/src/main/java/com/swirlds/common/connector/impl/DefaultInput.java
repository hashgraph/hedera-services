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

import com.swirlds.common.connector.Input;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class DefaultInput<DATA> implements Input<DATA> {

    private final Consumer<DATA> inputConsumer;

    private AtomicLong throughput = new AtomicLong(0);

    private final Executor executor;

    public DefaultInput(Consumer<DATA> inputConsumer, final Executor executor) {
        this.inputConsumer = inputConsumer;
        this.executor = executor;
    }

    public DefaultInput(Consumer<DATA> inputConsumer) {
        this(inputConsumer, Executors.newSingleThreadExecutor());
    }

    @Override
    public Consumer<DATA> getInputConsumer() {
        return data -> {
            executor.execute(() -> {
                inputConsumer.accept(data);
                throughput.incrementAndGet();
            });
        };
    }

    public long getThroughput() {
        return throughput.get();
    }
}
