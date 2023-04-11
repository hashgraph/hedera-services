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

package com.swirlds.common.threading.manager;

import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.utility.Lifecycle;

/**
 * A thread manager that has a start/stop lifecycle.
 */
public interface StartableThreadManager extends ThreadManager, Lifecycle, AutoCloseableNonThrowing {

    /**
     * {@inheritDoc}
     */
    @Override
    default void close() {
        stop();
    }
}
