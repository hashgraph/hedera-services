/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.config.sources;

import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.source.ConfigSource;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Abstract implementation of {@link ConfigSource}
 */
public abstract class AbstractConfigSource implements ConfigSource {

    /**
     * Provides all properties of this config source as {@link Map} instance
     *
     * @return all properties of this config
     */
    protected abstract Map<String, String> getInternalProperties();

    /**
     * {@inheritDoc}
     */
    @Override
    public final Set<String> getPropertyNames() {
        return getInternalProperties().keySet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final String getValue(final String propertyName) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        if (!getInternalProperties().containsKey(propertyName)) {
            throw new NoSuchElementException("Property " + propertyName + " is not defined");
        }
        return getInternalProperties().get(propertyName);
    }
}
