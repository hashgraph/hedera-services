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

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import org.junit.jupiter.api.Test;

class ScheduleEqualityVirtualKeySerializerTest {
    private final long longKey = 2;
    private final long otherLongKey = 3;

    private final ScheduleEqualityVirtualKeySerializer subject = new ScheduleEqualityVirtualKeySerializer();

    @Test
    void gettersWork() {
        assertEquals(BYTES_IN_SERIALIZED_FORM, subject.getSerializedSize());
        assertEquals(ScheduleEqualityVirtualKeySerializer.DATA_VERSION, subject.getCurrentDataVersion());
        assertEquals(ScheduleEqualityVirtualKeySerializer.CLASS_ID, subject.getClassId());
        assertEquals(ScheduleEqualityVirtualKeySerializer.CURRENT_VERSION, subject.getVersion());
    }

    @Test
    void deserializeWorks() {
        final BufferedData bin = BufferedData.allocate(Long.BYTES);
        final var expectedKey = new ScheduleEqualityVirtualKey(longKey);
        bin.writeLong(longKey);
        bin.reset();

        assertEquals(expectedKey, subject.deserialize(bin));
    }

    @Test
    void serializeWorks() {
        final BufferedData out = BufferedData.allocate(Long.BYTES);
        final BufferedData verify = BufferedData.allocate(Long.BYTES);

        final var virtualKey = new ScheduleEqualityVirtualKey(longKey);
        verify.writeLong(longKey);
        verify.reset();

        subject.serialize(virtualKey, out);
        assertEquals(BYTES_IN_SERIALIZED_FORM, out.position());
        out.reset();

        assertEquals(verify, out);
    }

    @Test
    void equalsUsingByteBufferWorks() {
        final var someKey = new ScheduleEqualityVirtualKey(longKey);
        final var diffNum = new ScheduleEqualityVirtualKey(otherLongKey);

        final BufferedData bin = BufferedData.allocate(Long.BYTES);
        bin.writeLong(someKey.getKeyAsLong());
        bin.reset();

        assertTrue(subject.equals(bin, someKey));
        bin.reset();

        assertFalse(subject.equals(bin, diffNum));
    }

    @Test
    void serdesAreNoop() {
        assertDoesNotThrow(() -> subject.deserialize((SerializableDataInputStream) null, 1));
        assertDoesNotThrow(() -> subject.serialize(null));
    }
}
