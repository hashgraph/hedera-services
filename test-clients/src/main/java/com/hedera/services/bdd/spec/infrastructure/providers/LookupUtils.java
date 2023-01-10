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
package com.hedera.services.bdd.spec.infrastructure.providers;

import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LookupUtils {
    public static Optional<Map.Entry<String, String>> twoDistinct(EntityNameProvider<?> provider) {
        Optional<String> first = provider.getQualifying();
        if (first.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> second = provider.getQualifyingExcept(Set.of(first.get()));
        if (second.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AbstractMap.SimpleEntry<>(first.get(), second.get()));
    }
}
