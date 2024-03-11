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

package com.swirlds.logging.api.extensions.handler;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.extensions.event.LogEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An abstract log handler that synchronizes the handling of log events. This handler is used as a base class for all
 * log handlers that need to synchronize the handling of log events like simple handlers that write events to the
 * console or to a file.
 */
public abstract class AbstractSyncedHandler extends AbstractLogHandler {

    /**
     * True if the log handler is stopped, false otherwise.
     */
    private volatile boolean stopped = false;

    /**
     * Creates a new log handler.
     *
     * @param configKey     the configuration key
     * @param configuration the configuration
     */
    public AbstractSyncedHandler(@NonNull final String configKey, @NonNull final Configuration configuration) {
        super(configKey, configuration);
    }

    @Override
    public final void accept(@NonNull LogEvent event) {
        if (stopped) {
            // FUTURE: is the emergency logger really the best idea in that case? If multiple handlers are stopped,
            // the emergency logger will be called multiple times.
            EMERGENCY_LOGGER.log(event);
        } else {
            handleEvent(event);
        }
    }

    /**
     * Handles the log event synchronously.
     *
     * @param event the log event
     */
    protected abstract void handleEvent(@NonNull LogEvent event);

    @Override
    public final void stopAndFinalize() {
        stopped = true;
        handleStopAndFinalize();
    }

    /**
     * Implementations can override this method to handle the stop and finalize of the handler. The method will be
     * called synchronously to the handling of log events.
     */
    protected void handleStopAndFinalize() {}
}
