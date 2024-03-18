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

package com.swirlds.logging.log4j.appender;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.extensions.event.LogEventConsumer;
import com.swirlds.logging.api.extensions.event.LogEventFactory;
import com.swirlds.logging.api.extensions.provider.AbstractLogProvider;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Install the {@link LogEventFactory} and {@link LogEventConsumer} to the {@link SwirldsLogAppender}.
 */
public class Log4JProvider extends AbstractLogProvider {

    /**
     * Name for the config key for the log provider.
     * The handler will be called with {@code logging.provider.log4j} prefix.
     */
    private static final String CONFIG_KEY = "log4j";

    /**
     * Creates a new log provider.
     *
     * @param configuration the configuration
     */
    public Log4JProvider(@NonNull final Configuration configuration) {
        super(CONFIG_KEY, configuration);
    }

    /**
     * Installs the {@link LogEventFactory} and {@link LogEventConsumer} to the {@link SwirldsLogAppender}.
     *
     * @param logEventFactory the log event factory
     * @param logEventConsumer the log event consumer
     */
    @Override
    public void install(
            @NonNull final LogEventFactory logEventFactory, @NonNull final LogEventConsumer logEventConsumer) {
        SwirldsLogAppender.setLogEventFactory(logEventFactory);
        SwirldsLogAppender.setLogEventConsumer(logEventConsumer);
    }
}
