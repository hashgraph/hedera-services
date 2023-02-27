/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.impl.internal;

import com.swirlds.config.api.Configuration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Class that contains functionality for the general parsing of list properties
 */
final class ConfigListUtils {

    private ConfigListUtils() {}

    /**
     * Returns a list based on the raw value
     *
     * @param rawValue
     * 		the raw value
     * @return the list
     */
    static List<String> createList(final String rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (Objects.equals(Configuration.EMPTY_LIST, rawValue)) {
            return List.of();
        }
        return Arrays.stream(rawValue.split(",")).toList();
    }
}
