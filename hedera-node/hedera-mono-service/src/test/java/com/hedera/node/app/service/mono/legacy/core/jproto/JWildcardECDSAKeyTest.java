/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.legacy.core.jproto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JWildcardECDSAKeyTest {
    JWildcardECDSAKey subject;
    byte[] bytes;

    @BeforeEach
    void setUp() {
        bytes = new byte[20];
        subject = new JWildcardECDSAKey(bytes, true);
    }

    @Test
    void emptyJHollowKeyTest() {
        var key1 = new JWildcardECDSAKey(null, true);
        assertTrue(key1.isEmpty());
        assertFalse(key1.isValid());

        var key2 = new JWildcardECDSAKey(new byte[0], true);
        assertTrue(key2.isEmpty());
        assertFalse(key2.isValid());
    }

    @Test
    void nonEmptyInvalidLengthJHollowKeyTest() {
        var key = new JWildcardECDSAKey(new byte[1], true);
        assertFalse(key.isEmpty());
        assertFalse(key.isValid());
    }

    @Test
    void constructorWorks() {
        var bytes = new byte[20];
        var subject = new JWildcardECDSAKey(bytes, true);

        assertTrue(subject.isValid());
        assertTrue(subject.hasWildcardECDSAKey());
        assertEquals(bytes, subject.getWildcardECDSAKey().getEvmAddress());
    }

    @Test
    void getterWorks() {
        assertEquals(bytes, subject.getWildcardECDSAKey().getEvmAddress());
    }
}
