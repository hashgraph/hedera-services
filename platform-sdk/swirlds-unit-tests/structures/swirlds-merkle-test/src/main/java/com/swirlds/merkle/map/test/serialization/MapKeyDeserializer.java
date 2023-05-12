/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.test.serialization;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.swirlds.merkle.map.test.pta.MapKey;

/**
 * Deserializer for MapKey
 */
public class MapKeyDeserializer extends KeyDeserializer {
    /**
     * {@inheritDoc}
     * Deserializes the MapKey
     * @param key String of a specific format that needs to be de-serialized to MapKey
     * @param deserializationContext
     * @return De-serialized MapKey with shardId, realmID, accountID
     */
    @Override
    public MapKey deserializeKey(String key, DeserializationContext deserializationContext) {

        String str = key.substring(key.indexOf("[") + 1, key.lastIndexOf("]"));
        String[] fields = str.split(",");
        return new MapKey(
                Long.parseLong(fields[0].trim()), Long.parseLong(fields[1].trim()), Long.parseLong(fields[2].trim()));
    }
}
