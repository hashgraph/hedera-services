/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.wires.input;

import com.swirlds.component.framework.schedulers.TaskScheduler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

/**
 * An object that knows how to add data to a {@link TaskScheduler} for processing, and how to forward data to a task
 * scheduler's output. This class is defined inside the input wire package to prevent anything that isn't an input wire
 * from accessing its methods.
 */
public abstract class TaskSchedulerInput<OUT> {

    /**
     * Add a task to the scheduler. May block if back pressure is enabled.
     *
     * @param handler handles the provided data
     * @param data    the data to be processed by the task scheduler
     */
    protected abstract void put(@NonNull Consumer<Object> handler, @NonNull Object data);

    /**
     * Add a task to the scheduler. If backpressure is enabled and there is not immediately capacity available, this
     * method will not accept the data.
     *
     * @param handler handles the provided data
     * @param data    the data to be processed by the scheduler
     * @return true if the data was accepted, false otherwise
     */
    protected abstract boolean offer(@NonNull Consumer<Object> handler, @NonNull Object data);

    /**
     * Inject data into the scheduler, doing so even if it causes the number of unprocessed tasks to exceed the capacity
     * specified by configured back pressure. If backpressure is disabled, this operation is logically equivalent to
     * {@link #put(Consumer, Object)}.
     *
     * @param handler handles the provided data
     * @param data    the data to be processed by the scheduler
     */
    protected abstract void inject(@NonNull Consumer<Object> handler, @NonNull Object data);

    /**
     * Pass data to this scheduler's primary output wire.
     * <p>
     * This method is implemented here to allow classes in this package to call forward(), which otherwise would not be
     * visible.
     */
    protected abstract void forward(@NonNull final OUT data);
}
