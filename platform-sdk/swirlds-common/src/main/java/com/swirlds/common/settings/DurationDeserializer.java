/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.settings;

import static com.swirlds.common.settings.ParsingUtils.parseDuration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.time.Duration;

/**
 * This object is capable of parsing durations, e.g. "3 seconds", "1 day", "2.5 hours", "32ms".
 * <p>
 * This deserializer currently utilizes a regex for parsing, which may have superlinear time complexity
 * for arbitrary input. Until that is addressed, do not use this parser on untrusted strings.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public class DurationDeserializer extends StdDeserializer<Duration> {

    private static final long serialVersionUID = 1;

    public DurationDeserializer() {
        super(Duration.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        return parseDuration(p.readValueAs(String.class));
    }
}
