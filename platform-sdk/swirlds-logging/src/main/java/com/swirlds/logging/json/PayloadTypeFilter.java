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

package com.swirlds.logging.json;

import static com.swirlds.logging.payloads.AbstractLogPayload.extractPayloadType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A filter that acts on payload types.
 */
public class PayloadTypeFilter implements Predicate<JsonLogEntry> {

    private final Set<String> types;

    public static PayloadTypeFilter payloadType(String... types) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, types);
        return new PayloadTypeFilter(set);
    }

    public static PayloadTypeFilter payloadType(List<String> types) {
        return new PayloadTypeFilter(new HashSet<>(types));
    }

    public static PayloadTypeFilter payloadType(Set<String> types) {
        return new PayloadTypeFilter(types);
    }

    /**
     * Create a filter that allows entries with certain types of payloads pass.
     *
     * @param types
     * 		a set of fully qualified payload type names
     */
    public PayloadTypeFilter(Set<String> types) {
        this.types = types;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean test(final JsonLogEntry entry) {
        if (types == null) {
            return false;
        }
        final String type = extractPayloadType(entry.getRawPayload());
        return types.stream().anyMatch(type::contains);
    }
}
