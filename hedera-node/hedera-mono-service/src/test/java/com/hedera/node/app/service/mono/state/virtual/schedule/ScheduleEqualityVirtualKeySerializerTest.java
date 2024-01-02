/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual.schedule;

import static com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey.BYTES_IN_SERIALIZED_FORM;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class ScheduleEqualityVirtualKeySerializerTest {
    private final long longKey = 2;
    private final long otherLongKey = 3;

    private final ScheduleEqualityVirtualKeySerializer subject = new ScheduleEqualityVirtualKeySerializer();

    @Test
    void gettersWork() {
        final ByteBuffer bin = ByteBuffer.allocate(Long.BYTES);

        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.deserializeKeySize(bin));
        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.getSerializedSize());
        assertEquals(ScheduleEqualityVirtualKeySerializer.DATA_VERSION, subject.getCurrentDataVersion());
        assertEquals(ScheduleEqualityVirtualKeySerializer.CLASS_ID, subject.getClassId());
        assertEquals(ScheduleEqualityVirtualKeySerializer.CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void deserializeWorks() throws IOException {
        final ByteBuffer bin = ByteBuffer.allocate(Long.BYTES);
        final var expectedKey = new ScheduleEqualityVirtualKey(longKey);
        bin.putLong(longKey);
        bin.rewind();

        assertEquals(expectedKey, subject.deserialize(bin, 1));
    }

    @Test
    void serializeWorks() throws IOException {
        final ByteBuffer out = ByteBuffer.allocate(Long.BYTES);
        final ByteBuffer verify = ByteBuffer.allocate(Long.BYTES);

        final var virtualKey = new ScheduleEqualityVirtualKey(longKey);
        verify.putLong(longKey);
        verify.rewind();
        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.serialize(virtualKey, out));
        out.rewind();

        assertEquals(verify, out);
    }

    @Test
    void equalsUsingByteBufferWorks() throws IOException {
        final var someKey = new ScheduleEqualityVirtualKey(longKey);
        final var diffNum = new ScheduleEqualityVirtualKey(otherLongKey);

        final ByteBuffer bin = ByteBuffer.allocate(Long.BYTES);
        bin.putLong(someKey.getKeyAsLong());
        bin.rewind();

        assertTrue(subject.equals(bin, 1, someKey));
        bin.rewind();

        assertFalse(subject.equals(bin, 1, diffNum));
    }

    @Test
    void serdesAreNoop() {
        assertDoesNotThrow(() -> subject.deserialize((SerializableDataInputStream) null, 1));
        assertDoesNotThrow(() -> subject.serialize(null));
    }
}
