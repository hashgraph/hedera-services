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
package com.hedera.node.app.hapi.fees.usage.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CryptoContextUtilsTest {
    @Test
    void getsChangedKeys() {
        final Map<Long, Long> newMap = new HashMap<>();
        final Map<Long, Long> existingMap = new HashMap<>();

        newMap.put(1L, 2L);
        newMap.put(3L, 2L);
        newMap.put(4L, 2L);

        existingMap.put(1L, 2L);
        existingMap.put(4L, 2L);
        existingMap.put(5L, 2L);

        assertEquals(
                1, CryptoContextUtils.getChangedCryptoKeys(newMap.keySet(), existingMap.keySet()));
    }

    @Test
    void getsChangedTokenKeys() {
        final Map<AllowanceId, Long> newMap = new HashMap<>();
        final Map<AllowanceId, Long> existingMap = new HashMap<>();

        newMap.put(new AllowanceId(1L, 2L), 2L);
        newMap.put(new AllowanceId(2L, 2L), 2L);
        newMap.put(new AllowanceId(3L, 2L), 2L);

        existingMap.put(new AllowanceId(1L, 2L), 2L);
        existingMap.put(new AllowanceId(4L, 2L), 2L);
        existingMap.put(new AllowanceId(3L, 5L), 2L);

        assertEquals(
                2, CryptoContextUtils.getChangedTokenKeys(newMap.keySet(), existingMap.keySet()));
    }
}
