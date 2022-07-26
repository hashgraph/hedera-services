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
package com.hedera.services.legacy.core.jproto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JECDSASecp256K1KeyTest {
    JECDSASecp256k1Key subject;
    byte[] bytes;

    @BeforeEach
    void setUp() {
        bytes = new byte[33];
        bytes[0] = 0x03;
        subject = new JECDSASecp256k1Key(bytes);
    }

    @Test
    void emptyJECDSAsecp256k1KeyTest() {
        JECDSASecp256k1Key key1 = new JECDSASecp256k1Key(null);
        assertTrue(key1.isEmpty());
        assertFalse(key1.isValid());

        JECDSASecp256k1Key key2 = new JECDSASecp256k1Key(new byte[0]);
        assertTrue(key2.isEmpty());
        assertFalse(key2.isValid());
    }

    @Test
    void nonEmptyInvalidLengthJECDSAsecp256k1KeyTest() {
        JECDSASecp256k1Key key = new JECDSASecp256k1Key(new byte[1]);
        assertFalse(key.isEmpty());
        assertFalse(key.isValid());
    }

    @Test
    void nonEmptyValid0x02JECDSAsecp256k1KeyTest() {
        bytes[0] = 0x02;
        JECDSASecp256k1Key key = new JECDSASecp256k1Key(bytes);
        assertFalse(key.isEmpty());
        assertTrue(key.isValid());
    }

    @Test
    void nonEmptyValid0x03JECDSAsecp256k1KeyTest() {
        assertFalse(subject.isEmpty());
        assertTrue(subject.isValid());
    }

    @Test
    void nonEmptyInvalid0x06JECDSAsecp256k1KeyTest() {
        bytes[0] = 0x06;
        assertFalse(subject.isEmpty());
        assertFalse(subject.isValid());
    }

    @Test
    void constructorWorks() {
        var bytes = new byte[33];
        bytes[0] = 0x03;
        var subject = new JECDSASecp256k1Key(bytes);

        assertEquals(bytes, subject.getECDSASecp256k1Key());
    }

    @Test
    void getterWorks() {
        assertEquals(bytes, subject.getECDSASecp256k1Key());
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "<JECDSASecp256k1Key: ecdsaSecp256k1Key "
                        + "hex=030000000000000000000000000000000000000000000000000000000000000000>",
                subject.toString());
    }

    @Test
    void equalsAndHashCodeWorks() {
        JECDSASecp256k1Key key1 = new JECDSASecp256k1Key("firstKey".getBytes());
        JECDSASecp256k1Key key2 = new JECDSASecp256k1Key("secondKey".getBytes());
        JECDSASecp256k1Key key3 = new JECDSASecp256k1Key("firstKey".getBytes());

        assertNotEquals(key1, key2);
        assertNotEquals(key1.hashCode(), key2.hashCode());
        assertEquals(key1, key3);
        assertEquals(key1.hashCode(), key3.hashCode());
        assertEquals(key1, key1);
        boolean forceEquals = key2.equals("sampleText");
        assertFalse(forceEquals);
        forceEquals = key1.equals(null);
        assertFalse(forceEquals);
    }
}
