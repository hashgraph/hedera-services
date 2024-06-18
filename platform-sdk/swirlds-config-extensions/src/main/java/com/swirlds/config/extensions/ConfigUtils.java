/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.config.extensions;

import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ConfigUtils {

    public static boolean haveSameProperties(
            @NonNull final Configuration config1, @NonNull final Configuration config2) {
        Objects.requireNonNull(config1, "config1 must not be null");
        Objects.requireNonNull(config2, "config2 must not be null");

        final List<String> names1 = config1.getPropertyNames().collect(Collectors.toList());
        final List<String> names2 = config2.getPropertyNames().collect(Collectors.toList());
        if (names1.size() != names2.size()) {
            return false;
        }
        for (int i = 0; i < names1.size(); i++) {
            if (!names2.contains(names1.get(i))) {
                return false;
            }
            final String name = names1.get(i);
            final String value1 = config1.getValue(name);
            final String value2 = config2.getValue(name);
            if (!Objects.equals(value1, value2)) {
                return false;
            }
        }
        return true;
    }
}
