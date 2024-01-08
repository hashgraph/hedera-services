/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.util;

import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.event.LogEventConsumer;

/**
 * A dummy implementation of the {@code LogEventConsumer} interface.
 *
 * <p>
 * This class serves as a placeholder for a log event consumer and provides an empty implementation
 * of the {@code accept} method. It can be used when you need to create a consumer instance that
 * does not perform any specific actions with log events.
 * </p>
 *
 * <p>
 * When you use an instance of {@code DummyConsumer}, the {@code accept} method does nothing
 * upon receiving a log event. It is particularly useful when you want to avoid processing or
 * handling log events in a specific scenario.
 * </p>
 */
public class DummyConsumer implements LogEventConsumer {
    /**
     * Accepts a log event but performs no action.
     *
     * @param event the log event to be accepted
     */
    @Override
    public void accept(LogEvent event) {
        // Empty implementation; does not perform any action
    }
}
