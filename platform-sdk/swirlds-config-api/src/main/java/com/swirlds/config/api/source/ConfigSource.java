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

package com.swirlds.config.api.source;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * This interface is used to define sources of configuration properties. Implementations of the interface can be added
 * to the configuration setup by calling * {@link com.swirlds.config.api.ConfigurationBuilder#withSource(ConfigSource)}
 */
public interface ConfigSource {

    /**
     * The default ordinal that is used if an implementation does not provide an ordinal by its own. See {@link
     * ConfigSource#getOrdinal()}
     */
    int DEFAULT_ORDINAL = 100;

    /**
     * Returns a set that contains all names of the properties that are provided by this source
     *
     * @return the set of property names
     */
    @NonNull
    Set<String> getPropertyNames();

    /**
     * Returns the String value of the property with the given name
     *
     * @param propertyName
     * @return the string value of the property
     * @throws NoSuchElementException if the property with the given name is not defined in the source
     */
    @Nullable
    String getValue(@NonNull String propertyName) throws NoSuchElementException;

    /**
     * Returns the ordinal. The ordinal is used to define a priority order of all config sources while the config source
     * with the highest ordinal has the highest priority. A config source will overwrite values of properties that are
     * already defined by a config source with a lower ordinal.
     *
     * @return the ordinal
     */
    default int getOrdinal() {
        return DEFAULT_ORDINAL;
    }

    /**
     * Returns all properties of this source as an immutable map.
     *
     * @return the map with all properties
     */
    @NonNull
    default Map<String, String> getProperties() {
        final Map<String, String> props = new HashMap<>();
        getPropertyNames().forEach(prop -> props.put(prop, getValue(prop)));
        return Collections.unmodifiableMap(props);
    }

    /**
     * Returns the name of the config source. A name must not be unique.
     *
     * @return the name
     */
    @NonNull
    default String getName() {
        return getClass().getName();
    }
}
