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

package com.hedera.node.config.sources;

import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public class DynamicConfigSource implements ConfigSource {

    private final Properties properties;

    public DynamicConfigSource() {
        this.properties = new Properties();
    }

    public void setProperty(@NonNull final String name, @Nullable final String value) {
        Objects.requireNonNull(name, "name cannot be null");
        properties.setProperty(name, value);
    }

    public void removeProperty(@NonNull final String name) {
        Objects.requireNonNull(name, "name cannot be null");
        properties.remove(name);
    }

    @NonNull
    @Override
    public Set<String> getPropertyNames() {
        return properties.stringPropertyNames();
    }

    @Nullable
    @Override
    public String getValue(@NonNull final String s) throws NoSuchElementException {
        return properties.getProperty(s);
    }

    @Override
    public int getOrdinal() {
        return 500;
    }
}
