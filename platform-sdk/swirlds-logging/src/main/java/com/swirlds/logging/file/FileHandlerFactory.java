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

package com.swirlds.logging.file;

import com.google.auto.service.AutoService;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.extensions.handler.LogHandlerFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.ServiceLoader;

/**
 * A factory for creating new {@link FileHandler} instances.
 * <p>
 * This is a {@link LogHandlerFactory} and is discovered by the {@link ServiceLoader} at runtime. The factory creates
 * new {@link FileHandler} instances with the specified {@link Configuration}.
 *
 * @see LogHandlerFactory
 * @see FileHandler
 * @see ServiceLoader
 * @see Configuration
 */
@AutoService(LogHandlerFactory.class)
public class FileHandlerFactory implements LogHandlerFactory {

    /**
     * The type name of the {@link FileHandler} used for {@code logging.handler.NAME.type} property.
     */
    public static final String FILE_HANDLER_TYPE = "file";

    /**
     * Creates a new {@link FileHandler} instance with the specified {@link Configuration}.
     *
     * @param handlerName   The name of the handler instance.
     * @param configuration The configuration for the new handler instance.
     * @return A new {@link FileHandler} instance.
     * @throws NullPointerException if the provided {@code configuration} is {@code null}.
     * @throws RuntimeException     if there was an error trying to create the {@link FileHandler}.
     */
    @NonNull
    @Override
    public LogHandler create(@NonNull final String handlerName, @NonNull final Configuration configuration) {
        try {
            return new FileHandler(handlerName, configuration);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create FileHandler", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getTypeName() {
        return FILE_HANDLER_TYPE;
    }
}
