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

import com.google.auto.service.AutoService;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.extensions.handler.LogHandlerFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A factory for creating {@link ConsoleHandler} instances.
 *
 * This class implements the {@link LogHandlerFactory} interface and is responsible for creating
 * instances of the {@link ConsoleHandler} class with the provided {@link Configuration}.
 *
 * @see LogHandlerFactory
 * @see ConsoleHandler
 * @see Configuration
 */
@AutoService(LogHandlerFactory.class)
public class ConsoleHandlerFactory implements LogHandlerFactory {

    public static final String CONSOLE_HANDLER_TYPE = "console";

    /**
     * Creates a new {@link ConsoleHandler} instance with the specified {@link Configuration}.
     *
     * @param handlerName The name of the handler instance.
     * @param configuration The configuration for the new handler instance.
     * @return A new {@link ConsoleHandler} instance.
     *
     * @throws NullPointerException if the provided {@code configuration} is {@code null}.
     */
    @Override
    @NonNull
    public LogHandler create(@NonNull final String handlerName, @NonNull final Configuration configuration) {
        return new ConsoleHandler(handlerName, configuration);
    }

    @NonNull
    @Override
    public String getTypeName() {
        return CONSOLE_HANDLER_TYPE;
    }
}
