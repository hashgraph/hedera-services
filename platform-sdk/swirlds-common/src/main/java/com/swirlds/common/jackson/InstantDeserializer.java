/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.common.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.Instant;

/**
 * Deserializer for optionally present {@link Instant} values.
 */
public class InstantDeserializer extends StdDeserializer<Instant> {
    public InstantDeserializer() {
        this(null);
    }

    public InstantDeserializer(Class<?> vc) {
        super(vc);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns {@code null} if a value is not present.
     */
    @Override
    public Instant deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException {
        final String value = jp.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }
}
