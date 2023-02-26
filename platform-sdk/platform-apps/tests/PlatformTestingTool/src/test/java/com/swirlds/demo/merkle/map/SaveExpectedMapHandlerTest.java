/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.merkle.map;

import static com.swirlds.merkle.map.test.lifecycle.SaveExpectedMapHandler.deserialize;
import static com.swirlds.merkle.map.test.lifecycle.SaveExpectedMapHandler.serialize;
import static com.swirlds.merkle.map.test.lifecycle.TransactionType.Update;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.Hash;
import com.swirlds.merkle.map.test.lifecycle.EntityType;
import com.swirlds.merkle.map.test.lifecycle.ExpectedValue;
import com.swirlds.merkle.map.test.lifecycle.LifecycleStatus;
import com.swirlds.merkle.map.test.lifecycle.SaveExpectedMapHandler;
import com.swirlds.merkle.map.test.lifecycle.TransactionState;
import com.swirlds.merkle.map.test.pta.MapKey;
import java.io.File;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class to serialize and deserialize the ExpectedMap to/from JSON in the class SaveExpectedMapHandler
 */
public class SaveExpectedMapHandlerTest {
    private static Map<MapKey, ExpectedValue> expectedMap;
    private static Map<MapKey, ExpectedValue> deserializedMap;
    private static Random random = new Random();
    private static SaveExpectedMapHandler handler;
    private static final int contentSize = 48;
    private static final String expectedMapName = "ExpectedMap.json";
    private static final String expectedMapZip = "ExpectedMap.json.gz";

    @BeforeEach
    public void init() {
        expectedMap = new HashMap<>();
        deserializedMap = new HashMap<>();
        handler = new SaveExpectedMapHandler();
        addToMap();
        cleanDirectory();
    }

    // clean existing directory
    private void cleanDirectory() {
        File jsonMap = new File(expectedMapName);
        File jsonZip = new File(expectedMapZip);
        try {
            if (jsonMap.exists()) jsonMap.delete();
            if (jsonZip.exists()) jsonZip.delete();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Add Keys to ExpectedMap
    private void addToMap() {
        for (int i = 0; i < 20; i++) {
            MapKey mk = new MapKey(0, 0, i);

            LifecycleStatus submitLS = new LifecycleStatus(
                    TransactionState.SUBMITTED, Update, Instant.now().getEpochSecond(), i);
            LifecycleStatus handleLS = new LifecycleStatus(
                    TransactionState.HANDLED, Update, Instant.now().getEpochSecond(), i);

            expectedMap.put(
                    mk,
                    new ExpectedValue(
                            EntityType.Crypto, new Hash(generateRandomContent()), true, submitLS, handleLS, null, 0));
        }
    }

    // Add invalid keys to expectedMap
    private void addInvalidKeysToMap() {
        for (int i = 20; i < 25; i++) {
            MapKey mk = new MapKey(0, 0, i);
            expectedMap.put(mk, new ExpectedValue(null, new Hash(generateRandomContent()), false, null, null, null, 0));
        }
    }

    // generate random bytes to generate Hash
    private byte[] generateRandomContent() {
        final byte[] content = new byte[contentSize];
        random.nextBytes(content);
        return content;
    }

    // Verify if actual HashMap and deserialized HashMap are equal
    private boolean areEqual(Map<MapKey, ExpectedValue> actualMap, Map<MapKey, ExpectedValue> expectedMap) {
        if (actualMap.size() != expectedMap.size()) {
            return false;
        }

        return actualMap.entrySet().stream().allMatch(e -> e.getValue().equals(expectedMap.get(e.getKey())));
    }

    // Serializes and deserializes the expected map with all valid keys
    @Test
    public void serializeAndDeserializePositiveTest() {
        String jsonValue = serialize(expectedMap, new File("."), expectedMapName, true);
        for (int i = 0; i < 20; i++) {
            assertTrue(jsonValue.contains("[0,0," + i + "]"));
        }

        deserializedMap = deserialize(new File(".", expectedMapZip));

        assertTrue(areEqual(expectedMap, deserializedMap));
    }

    // serializes and deserializes expectedMap with null EntityType ExpectedValues.
    @Test
    public void DeserializeNullEntityTypeTest() {
        addInvalidKeysToMap();
        String jsonValue = serialize(expectedMap, new File("."), expectedMapName, true);

        for (int i = 20; i < 25; i++) {
            assertTrue(jsonValue.contains("MapKey[0,0," + i + "]"));
        }

        deserializedMap = deserialize(new File(".", expectedMapZip));
        assertEquals(null, deserializedMap.get(new MapKey(0, 0, 24)).getEntityType());
    }
}
