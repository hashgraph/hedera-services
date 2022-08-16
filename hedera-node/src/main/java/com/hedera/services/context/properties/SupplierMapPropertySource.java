/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Implements a {@link PropertySource} by looking up property values in a {@link Map} of {@link
 * Supplier} instances.
 */
public class SupplierMapPropertySource implements PropertySource {
    private final Map<String, Supplier<Object>> source;

    public SupplierMapPropertySource(Map<String, Supplier<Object>> source) {
        this.source = source;
    }

    @Override
    public boolean containsProperty(String name) {
        return source.containsKey(name);
    }

    @Override
    public Object getProperty(String name) {
        return source.get(name).get();
    }

    @Override
    public Set<String> allPropertyNames() {
        return source.keySet();
    }
}
