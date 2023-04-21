/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.api.intern;

import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.spi.ConfigurationBuilderFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * This class is the only private API of the config API that is used to load an implementation of the config API by
 * using the Java SPI (see {@link ServiceLoader}).
 */
public final class ConfigurationProvider {

    private static final ConfigurationProvider INSTANCE = new ConfigurationProvider();

    private final ConfigurationBuilderFactory factory;

    private ConfigurationProvider() {
        factory = loadImplementation(ConfigurationProvider.class.getClassLoader());
    }

    @NonNull
    private static ConfigurationBuilderFactory loadImplementation(@NonNull final ClassLoader classloader) {
        final ServiceLoader<ConfigurationBuilderFactory> serviceLoader =
                ServiceLoader.load(ConfigurationBuilderFactory.class, classloader);
        final Iterator<ConfigurationBuilderFactory> iterator = serviceLoader.iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        }
        throw new IllegalStateException("No ConfigurationBuilderFactory implementation found!");
    }

    /**
     * This method is the facade against an implementation of the config API. The method returns a new {@link
     * ConfigurationBuilder} instance that is created by a concrete implementation.
     *
     * @return a new {@link ConfigurationBuilder} instance
     */
    public ConfigurationBuilder createBuilder() {
        return factory.create();
    }

    /**
     * Access to this class that is defined by a singleton
     *
     * @return the singleton instance of this class
     */
    public static ConfigurationProvider getInstance() {
        return INSTANCE;
    }
}
