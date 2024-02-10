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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Setting;
import com.swirlds.config.extensions.sources.AbstractConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SettingsConfigSource extends AbstractConfigSource {

    private final Map<String, String> properties;
    private final int ordinal;

    public SettingsConfigSource(@NonNull final List<Setting> settings, int ordinal) {
        requireNonNull(settings, "settings must not be null");
        this.ordinal = ordinal;

        final var mutableMap = new HashMap<String, String>();
        for (final var setting : settings) {
            mutableMap.put(setting.name(), setting.value());
        }

        properties = Collections.unmodifiableMap(mutableMap);
    }

    @Override
    protected Map<String, String> getInternalProperties() {
        return properties;
    }

    @Override
    public int getOrdinal() {
        return ordinal;
    }
}
