/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.context.properties;

import java.util.HashSet;
import java.util.Set;

public class ChainedSources implements PropertySource {

    private final PropertySource first;
    private final PropertySource second;

    public ChainedSources(final PropertySource first, final PropertySource second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean containsProperty(final String name) {
        return first.containsProperty(name) || second.containsProperty(name);
    }

    @Override
    public Object getProperty(final String name) {
        return first.containsProperty(name) ? first.getProperty(name) : second.getProperty(name);
    }

    @Override
    public Set<String> allPropertyNames() {
        final var all = new HashSet<>(first.allPropertyNames());
        all.addAll(second.allPropertyNames());
        return all;
    }

    @Override
    public String getRawValue(final String name) {
        return first.containsProperty(name) ? first.getRawValue(name) : second.getRawValue(name);
    }
}
