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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swirlds.logging.SwirldsLogParser;

/**
 * A parser that reads logs in json format.
 */
public class JsonParser implements SwirldsLogParser<JsonLogEntry> {

    private static final JsonFactory factory = new JsonFactory();

    @Override
    public JsonLogEntry parse(String line) {
        ObjectMapper mapper = new ObjectMapper(factory);
        JsonNode rootNode;
        try {
            rootNode = mapper.readTree(line);
        } catch (JsonProcessingException e) {
            return null;
        }

        if (rootNode == null) {
            return null;
        }

        return new JsonLogEntry(rootNode);
    }
}
