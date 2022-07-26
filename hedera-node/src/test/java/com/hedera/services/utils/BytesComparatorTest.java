/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class BytesComparatorTest {
    @Test
    void compareTest() {
        final var subject = BytesComparator.INSTANCE;
        final var first = ByteString.copyFromUtf8("somebytes1").toByteArray();
        final var second = ByteString.copyFromUtf8("somebytes2").toByteArray();
        final var differentSize = ByteString.copyFromUtf8("differentsize").toByteArray();

        var result = subject.compare(Bytes.of(first), Bytes.of(second));
        assertEquals(-1, result);

        result = subject.compare(Bytes.of(first), Bytes.of(first));
        assertEquals(0, result);

        result = subject.compare(null, Bytes.of(first));
        assertEquals(1, result);

        result = subject.compare(Bytes.of(first), null);
        assertEquals(-1, result);

        result = subject.compare(null, null);
        assertEquals(0, result);

        result = subject.compare(Bytes.of(differentSize), Bytes.of(first));
        assertEquals(1, result);
    }
}
