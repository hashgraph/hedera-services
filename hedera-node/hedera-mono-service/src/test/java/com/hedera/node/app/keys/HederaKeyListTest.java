/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.keys;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.keys.impl.HederaEd25519Key;
import com.hedera.node.app.keys.impl.HederaKeyList;
import com.hedera.node.app.keys.impl.HederaThresholdKey;
import com.hedera.node.app.spi.keys.HederaReplKey;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HederaKeyListTest {
    private static final int ED25519_BYTE_LENGTH = 32;
    private HederaKeyList subject;

    @Test
    void nullKeyListInConstructorIsNotAllowed() {
        assertThrows(
                NullPointerException.class, () -> new HederaKeyList((List<HederaReplKey>) null));
    }

    @Test
    void emptyKeyListIsNotValid() {
        subject = new HederaKeyList(List.of());
        assertTrue(subject.isEmpty());
        assertFalse(subject.isValid());
    }

    @Test
    void invalidKeyInKeyListFails() {
        subject =
                new HederaKeyList(
                        List.of(
                                new HederaEd25519Key(new byte[1]),
                                new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH])));
        assertFalse(subject.isEmpty());
        assertFalse(subject.isValid());

        subject =
                new HederaKeyList(List.of(new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH - 1])));
        assertFalse(subject.isEmpty());
        assertFalse(subject.isValid());
    }

    @Test
    void validKeyInKeyListWorks() {
        subject = new HederaKeyList(List.of(new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH])));
        assertFalse(subject.isEmpty());
        assertTrue(subject.isValid());
    }

    @Test
    void testsIsPrimitive() {
        subject = new HederaKeyList(List.of(new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH])));
        assertFalse(subject.isPrimitive());
    }

    @Test
    void copyAndAsReadOnlyWorks() {
        subject = new HederaKeyList(List.of(new HederaEd25519Key(new byte[ED25519_BYTE_LENGTH])));
        final var copy = subject.copy();
        assertEquals(subject, copy);
        assertNotSame(subject, copy);
    }

    @Test
    void equalsAndHashCodeWorks() {
        final var key1 = new HederaKeyList(List.of(new HederaEd25519Key("firstKey".getBytes())));
        final var key2 = new HederaKeyList(List.of(new HederaEd25519Key("secondKey".getBytes())));
        final var key3 = new HederaKeyList(List.of(new HederaEd25519Key("firstKey".getBytes())));

        assertNotEquals(key1, key2);
        assertNotEquals(key1.hashCode(), key2.hashCode());
        assertEquals(key1, key3);
        assertEquals(key1.hashCode(), key3.hashCode());
        assertEquals(key1, key1);
        assertNotEquals(null, key1);
    }

    @Test
    void toStringWorks() {
        subject = new HederaKeyList(List.of(new HederaEd25519Key("firstKey".getBytes())));
        final var expectedString = "HederaKeyList[keys=[HederaEd25519Key[key=66697273744b6579]]]";
        assertEquals(expectedString, subject.toString());
    }

    @Test
    void visitsAllSimpleKeys() {
        final var k1 = new HederaEd25519Key("test".getBytes());
        final var k2 = new HederaEd25519Key("test-again".getBytes());
        final var key = new HederaKeyList(List.of(k1, k2));

        final var visitedPrimitiveKeys = new ArrayList<>();
        key.visitPrimitiveKeys(simpleKey -> visitedPrimitiveKeys.add(simpleKey));

        assertThat(visitedPrimitiveKeys, contains(k1, k2));
    }
}
