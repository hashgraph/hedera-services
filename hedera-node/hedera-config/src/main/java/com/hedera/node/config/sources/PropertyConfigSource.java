/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.config.sources;

import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * A config source that wraps a {@link Properties} object.
 */
public class PropertyConfigSource implements ConfigSource {

    private final Properties properties;

    private final int ordinal;

    public PropertyConfigSource(@NonNull final Properties properties, final int ordinal) {
        Objects.requireNonNull(properties, "properties must not be null");
        this.properties = new Properties();
        properties.forEach((key, value) -> this.properties.put(key.toString(), value.toString()));
        this.ordinal = ordinal;
    }

    public PropertyConfigSource(@NonNull final Properties properties) {
        this(properties, ConfigSource.DEFAULT_ORDINAL);
    }

    @NonNull
    @Override
    public Set<String> getPropertyNames() {
        return properties.stringPropertyNames();
    }

    @Nullable
    @Override
    public String getValue(@NonNull String key) throws NoSuchElementException {
        Objects.requireNonNull(key, "key must not be null");
        return properties.getProperty(key);
    }

    @Override
    public int getOrdinal() {
        return ordinal;
    }
}
