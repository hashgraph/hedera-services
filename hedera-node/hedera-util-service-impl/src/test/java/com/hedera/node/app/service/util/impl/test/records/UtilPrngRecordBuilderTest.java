/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.util.impl.test.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.util.impl.records.UtilPrngRecordBuilder;
import com.hedera.node.app.spi.fixtures.Utils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UtilPrngRecordBuilderTest {
    private UtilPrngRecordBuilder subject;

    @BeforeEach
    void setUp() {
        subject = new UtilPrngRecordBuilder();
    }

    @Test
    void emptyConstructor() {
        assertNull(subject.getPrngBytes());
        assertNull(subject.getPrngNumber());
        assertFalse(subject.hasPrngNumber());
    }

    @Test
    void gettersAndSettersForBytesWork() {
        final var randomBytes = Utils.randomUtf8Bytes(48);

        subject.setPrngBytes(Bytes.wrap(randomBytes));

        assertEquals(Bytes.wrap(randomBytes), subject.getPrngBytes());
        assertFalse(subject.hasPrngNumber());
        assertTrue(subject.hasPrngBytes());
    }

    @Test
    void gettersAndSettersForNumberWork() {
        subject.setPrngNumber(123456789);

        assertEquals(123456789, subject.getPrngNumber());
        assertTrue(subject.hasPrngNumber());
        assertFalse(subject.hasPrngBytes());
    }
}
