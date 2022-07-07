/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle.internals;

import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.CONTRACT_BYTECODE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.CONTRACT_STORAGE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_DATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_METADATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.SYSTEM_DELETED_ENTITY_EXPIRY;
import static com.hedera.services.state.merkle.internals.BlobKey.typeFromCharCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlobKeyTest {
    private BlobKey subject;

    @BeforeEach
    void setup() {
        subject = new BlobKey(FILE_DATA, 2);
    }

    @Test
    void gettersWork() {
        assertEquals(FILE_DATA, subject.type());
        assertEquals(2, subject.entityNum());
    }

    @Test
    void objectContractMet() {
        final var one = new BlobKey(FILE_METADATA, 2);
        final var two = new BlobKey(FILE_DATA, 2);
        final var three = new BlobKey(FILE_DATA, 2);
        final var twoRef = two;

        assertNotEquals(two, one);
        assertEquals(two, twoRef);
        assertEquals(two, three);
        assertNotEquals(null, one);

        assertNotEquals(one.hashCode(), two.hashCode());
        assertEquals(two.hashCode(), three.hashCode());
    }

    @Test
    void legacyBlobCodeSupported() {
        assertEquals(FILE_DATA, typeFromCharCode('f'));
        assertEquals(FILE_METADATA, typeFromCharCode('k'));
        assertEquals(CONTRACT_BYTECODE, typeFromCharCode('s'));
        assertEquals(CONTRACT_STORAGE, typeFromCharCode('d'));
        assertEquals(SYSTEM_DELETED_ENTITY_EXPIRY, typeFromCharCode('e'));

        final var iae = assertThrows(IllegalArgumentException.class, () -> typeFromCharCode('a'));
        assertEquals("Invalid legacy code 'a'", iae.getMessage());
    }
}
