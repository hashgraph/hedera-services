/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractAsyncHandler extends AbstractLogHandler {
    /**
     * The executor service that is used to handle log events asynchronously.
     */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * True if the log handler is stopped, false otherwise.
     */
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    /**
     * Creates a new log handler.
     *
     * @param configKey     the configuration key
     * @param configuration the configuration
     */
    public AbstractAsyncHandler(@NonNull final String configKey, @NonNull final Configuration configuration) {
        super(configKey, configuration);
    }

    @Override
    public void accept(@NonNull LogEvent event) {
        if (stopped.get()) {
            EMERGENCY_LOGGER.log(event);
            return;
        }
        executorService.submit(() -> {
            if (stopped.get()) {
                EMERGENCY_LOGGER.log(event);
            } else {
                handleEvent(event);
            }
        });
    }

    /**
     * Handles the log event asynchronously.
     *
     * @param event the log event
     */
    protected abstract void handleEvent(@NonNull LogEvent event);

    @Override
    public void stopAndFinalize() {
        if (!stopped.getAndSet(true)) {
            executorService.shutdown();
            handleStopAndFinalize();
        }
    }

    /**
     * Implementations can override this method to handle the stop and finalize of the handler.
     */
    protected void handleStopAndFinalize() {}
}
