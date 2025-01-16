/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.config.extensions.sources;

import com.swirlds.base.ArgumentUtils;
import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A {@link com.swirlds.config.api.source.ConfigSource} implementation that provides values from the system properties.
 * The class is defined as a singleton.
 */
public final class SystemEnvironmentConfigSource implements ConfigSource {

    private static SystemEnvironmentConfigSource instance;

    private SystemEnvironmentConfigSource() {}

    /**
     * Returns the singleton.
     *
     * @return the singleton
     */
    public static SystemEnvironmentConfigSource getInstance() {
        if (instance == null) {
            synchronized (SystemEnvironmentConfigSource.class) {
                if (instance != null) {
                    return instance;
                }
                instance = new SystemEnvironmentConfigSource();
            }
        }
        return instance;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Set<String> getPropertyNames() {
        return System.getenv().keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getValue(@NonNull final String propertyName) {
        ArgumentUtils.throwArgBlank(propertyName, "propertyName");
        if (!getPropertyNames().contains(propertyName)) {
            throw new NoSuchElementException("Property " + propertyName + " is not defined");
        }
        return System.getenv(propertyName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isListProperty(@NonNull final String propertyName) throws NoSuchElementException {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<String> getListValue(@NonNull final String propertyName) throws NoSuchElementException {
        return List.of();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrdinal() {
        return ConfigSourceOrdinalConstants.SYSTEM_ENVIRONMENT_ORDINAL;
    }
}
