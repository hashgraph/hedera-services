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
import com.swirlds.common.wiring.counters.AbstractObjectCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@link Wire} that permits parallel execution of tasks. Similar to {@link ConcurrentWire} but with extra metering.
 *
 * @param <T> the type of object that is passed through the wire
 */
public class MeteredConcurrentWire<T> implements Wire<T> {
    private Consumer<T> consumer;
    private final AbstractObjectCounter onRamp;
    private final AbstractObjectCounter offRamp;
    private final String name;

    // TODO write unit tests for this class

    /**
     * Constructor.
     *
     * @param name    the name of the wire
     * @param onRamp  an object counter that is incremented when data is added to the wire, ignored if null
     * @param offRamp an object counter that is decremented when data is removed from the wire, ignored if null
     */
    public MeteredConcurrentWire(
            @NonNull final String name,
            @Nullable final AbstractObjectCounter onRamp,
            @Nullable final AbstractObjectCounter offRamp) {
        this.name = Objects.requireNonNull(name);
        this.onRamp = onRamp == null ? NoOpCounter.getInstance() : onRamp;
        this.offRamp = offRamp == null ? NoOpCounter.getInstance() : offRamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConsumer(@NonNull Consumer<T> consumer) {
        if (this.consumer != null) {
            throw new IllegalStateException("Consumer has already been set");
        }
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
        onRamp.onRamp();
        new AbstractTask() {
            @Override
            protected boolean exec() {
                offRamp.offRamp();
                consumer.accept(data);
                return true;
            }
        }.send();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptablePut(@NonNull T data) throws InterruptedException {
        onRamp.interruptableOnRamp();
        new AbstractTask() {
            @Override
            protected boolean exec() {
                offRamp.offRamp();
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
        boolean accepted = onRamp.attemptOnRamp();
        if (!accepted) {
            return false;
        }
        new AbstractTask() {
            @Override
            protected boolean exec() {
                offRamp.offRamp();
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
        return onRamp.getCount();
    }
}
