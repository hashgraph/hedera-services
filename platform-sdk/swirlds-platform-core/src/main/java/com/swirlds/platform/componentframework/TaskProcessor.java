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

package com.swirlds.platform.componentframework;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import java.util.Map;

/**
 * <p>
 * A task processor is a component that is handed tasks which are processed asynchronously. These handoff methods are
 * the only interaction with the task processor. The implementation of the task processor does not have to concern
 * itself with any threading concerns, as the framework will handle that.
 * </p>
 * <p>
 * This is the top level interface for all task processing components. A task processor must declare an interface that
 * extends this one and has at least one method that has the same signature as a {@link InterruptableConsumer}.
 * References to these methods should be returned by {@link #getProcessingMethods()}. There should also be at least one
 * implementation that actually handles the tasks.
 * </p>
 * <p>
 * There will be two instances of a task processor interface, one for submitting the tasks and one for processing them.
 * </p>
 */
public interface TaskProcessor {

    /**
     * Returns a map of references to the task processing methods. The key is the type of the task and the value is a
     * reference to the method that processes the task.
     *
     * @return a map of references to the task processing methods
     */
    Map<Class<?>, InterruptableConsumer<?>> getProcessingMethods();
}
