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

package com.swirlds.platform.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The configuration mappings that can be used to remap the old properties to the new properties.
 */
public final class ConfigMappings {

    /**
     * Please add configuration mappings to this map with a pair of the old and new property names.
     * For example, {@code mappings.put("oldName", "prefix.newName");}
     */
    private static Map<String, String> mappings;

    static {
        mappings = new HashMap<>();
        // Please add new configuration mappings here.

        mappings = Collections.unmodifiableMap(mappings);
    }

    private ConfigMappings() {}

    /**
     * Getter of the configuration mappings.
     *
     * @return the configuration mappings
     */
    public static Map<String, String> getConfigMappings() {
        return mappings;
    }
}
