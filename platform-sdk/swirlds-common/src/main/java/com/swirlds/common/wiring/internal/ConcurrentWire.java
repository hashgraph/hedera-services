/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.internal;

import com.swirlds.common.wiring.Wire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@link Wire} that permits parallel execution of tasks.
 *
 * @param <T> the type of object that is passed through the wire
 */
public class ConcurrentWire<T> implements Wire<T> {
    private final Consumer<T> consumer;
    private final String name;

    // TODO write unit tests for this class

    /**
     * Constructor.
     *
     * @param name    the name of the wire
     * @param consumer data on the wire is passed to this consumer
     */
    public ConcurrentWire(@NonNull final String name, @NonNull final Consumer<T> consumer) {
        this.name = Objects.requireNonNull(name);
        this.consumer = Objects.requireNonNull(consumer);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(@NonNull final T data) {
        new AbstractTask() {
            @Override
            protected boolean exec() {
                consumer.accept(data);
                return true;
            }
        }.send();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptablePut(@NonNull T data) {
        new AbstractTask() {
            @Override
            protected boolean exec() {
                consumer.accept(data);
                return true;
            }
        }.send();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean offer(@NonNull T data) {
        new AbstractTask() {
            @Override
            protected boolean exec() {
                consumer.accept(data);
                return true;
            }
        }.send();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getUnprocessedTaskCount() {
        return -1;
    }
}
