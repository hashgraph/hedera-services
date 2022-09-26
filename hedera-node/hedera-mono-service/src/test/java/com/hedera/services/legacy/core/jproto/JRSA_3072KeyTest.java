/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.legacy.core.jproto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JRSA_3072KeyTest {
    @Test
    void emptyJRSA_3072KeyTest() {
        JRSA_3072Key key1 = new JRSA_3072Key(null);
        assertTrue(key1.isEmpty());
        assertFalse(key1.isValid());

        JRSA_3072Key key2 = new JRSA_3072Key(new byte[0]);
        assertTrue(key2.isEmpty());
        assertFalse(key2.isValid());
    }

    @Test
    void nonEmptyJRSA_3072KeyTest() {
        final var desiredRepr = "<JRSA_3072Key: rsa3072Key hex=4e4f4e53454e5345>";
        final var mockKey = "NONSENSE".getBytes();
        JRSA_3072Key key = new JRSA_3072Key(mockKey);
        assertFalse(key.isEmpty());
        assertTrue(key.isValid());
        assertEquals(desiredRepr, key.toString());
    }
}
