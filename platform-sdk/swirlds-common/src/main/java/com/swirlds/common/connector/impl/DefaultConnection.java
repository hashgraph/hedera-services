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
import com.swirlds.common.connector.Output;
import com.swirlds.common.connector.Subscription;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class DefaultConnection<DATA> implements Connection<DATA> {

    private AtomicLong throughput = new AtomicLong(0);

    private final AtomicReference<Subscription> internalConnection = new AtomicReference<>();


    public <DATA> DefaultConnection(Output<DATA> output, Consumer<DATA> inputConsumer) {
        internalConnection.set(output.connect(inputConsumer));
    }

    @Override
    public long getThroughput() {
        return throughput.get();
    }

    @Override
    public void close() {
        internalConnection.getAndUpdate(subscription -> {
            if (subscription != null) {
                subscription.unscubscribe();
            }
            return null;
        });
    }

    @Override
    public boolean isClosed() {
        return internalConnection.get() == null;
    }
}
