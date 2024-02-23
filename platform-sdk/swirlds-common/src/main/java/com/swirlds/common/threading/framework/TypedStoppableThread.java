/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.framework;

import com.swirlds.common.threading.interrupt.InterruptableRunnable;

/**
 * A {@link StoppableThread} that is aware of the type of object being used to do work.
 *
 * @param <T>
 * 		the type of object used to do work
 */
public interface TypedStoppableThread<T extends InterruptableRunnable> extends StoppableThread {

    /**
     * Get the object used to do work.
     *
     * @return the object used to do work
     */
    T getWork();
}
