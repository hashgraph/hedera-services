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

import com.swirlds.common.connector.Publisher;
import com.swirlds.common.connector.Subscription;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class DefaultPublisher<DATA> implements Publisher<DATA> {

    private final List<Consumer<DATA>> consumers = new CopyOnWriteArrayList<>();

    @Override
    public Subscription connect(final Consumer<DATA> consumer) {
        consumers.add(consumer);
        return new Subscription() {
            @Override
            public void unscubscribe() {
                consumers.remove(consumer);
            }
        };
    }

    @Override
    public void publish(DATA data) {
        consumers.forEach(consumer -> consumer.accept(data));
    }
}
