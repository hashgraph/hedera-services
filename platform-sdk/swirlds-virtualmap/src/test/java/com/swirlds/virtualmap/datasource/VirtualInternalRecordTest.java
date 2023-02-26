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

package com.swirlds.virtualmap.datasource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class VirtualInternalRecordTest {
    private static final Cryptography CRYPTO = CryptographyHolder.get();

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Using the path Constructor works")
    void createInternalRecordUsingPathConstructor() {
        final VirtualInternalRecord rec = new VirtualInternalRecord(101);
        assertNull(rec.getHash(), "hash should be null");
        assertEquals(101, rec.getPath(), "path should match expected");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("Using the full constructor works")
    void createInternalRecordUsingPathHashConstructor() {
        final Hash hash = CRYPTO.digestSync("Fake Hash".getBytes(StandardCharsets.UTF_8));
        final VirtualInternalRecord rec = new VirtualInternalRecord(102, hash);
        assertEquals(hash, rec.getHash(), "hash should match");
        assertEquals(102, rec.getPath(), "path should match expected");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.VMAP)
    @DisplayName("toString with a null hash is OK")
    void toStringWithNullHashDoesNotThrow() {
        final VirtualInternalRecord rec = new VirtualInternalRecord(103);
        final String str = rec.toString();
        assertNotNull(str, "value should not be null");
    }
}
