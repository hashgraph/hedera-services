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

package com.swirlds.logging.console;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.AbstractSyncedHandler;
import com.swirlds.logging.api.internal.format.LineBasedFormat;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A handler that logs events to the console.
 *
 * This class extends the {@link AbstractSyncedHandler} and provides a simple way to log
 * {@link LogEvent}s to the console using a {@link LineBasedFormat}.
 *
 * @see AbstractSyncedHandler
 * @see LineBasedFormat
 */
public class ConsoleHandler extends AbstractSyncedHandler {

    private final LineBasedFormat format;

    /**
     * Constructs a new ConsoleHandler with the specified configuration.
     *
     * @param handlerName   The unique name of this handler.
     * @param configuration The configuration for this handler.
     */
    public ConsoleHandler(@NonNull final String handlerName, @NonNull final Configuration configuration) {
        super(handlerName, configuration);
        format = LineBasedFormat.createForHandler(handlerName, configuration);
    }

    /**
     * Handles a log event by printing it to the console using the {@link LineBasedFormat},
     * followed by flushing the console output.
     *
     * @param event The log event to be printed.
     */
    @Override
    protected void handleEvent(@NonNull final LogEvent event) {
        format.print(System.out, event);
        System.out.flush();
    }
}
