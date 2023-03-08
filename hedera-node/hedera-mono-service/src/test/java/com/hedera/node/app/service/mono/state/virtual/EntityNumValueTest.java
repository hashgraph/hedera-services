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

package com.hedera.node.app.service.mono.state.virtual;

import static com.hedera.node.app.service.mono.state.virtual.EntityNumValue.RUNTIME_CONSTRUCTABLE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.swirlds.common.exceptions.MutabilityException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class EntityNumValueTest {
    private EntityNumValue subject = new EntityNumValue(2L);

    @Test
    void gettersWork() {
        assertEquals(2L, subject.num());
        assertEquals(1, subject.getVersion());
        assertEquals(RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    @Test
    void copyWorks() {
        var virtualValue = subject.copy();

        assertEquals(subject.num(), virtualValue.num());
    }

    @Test
    void asReadOnlyWorks() {
        var virtualValue = subject.asReadOnly();
        assertNotEquals(subject, virtualValue);
        assertEquals(true, virtualValue.isImmutable());
    }

    @Test
    void deserializeWorksWithBuffer() throws IOException {
        final var bin = mock(ByteBuffer.class);
        final var expectedKey = new EntityNumValue();
        given(bin.getLong()).willReturn(2L);

        expectedKey.deserialize(bin, 1);

        assertEquals(2L, expectedKey.num());
    }

    @Test
    void deserializeWorksWithStream() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        final var expectedKey = new EntityNumValue();
        given(in.readLong()).willReturn(2L);

        expectedKey.deserialize(in, 1);

        assertEquals(2L, expectedKey.num());
    }

    @Test
    void serializeWorksWithBuffer() throws IOException {
        final var out = mock(ByteBuffer.class);

        subject.serialize(out);

        verify(out).putLong(2L);
    }

    @Test
    void cannotDeserializeForImmutableValue() {
        final var immutable = subject.asReadOnly();
        final var buffer = ByteBuffer.allocate(0);
        assertThrows(MutabilityException.class, () -> immutable.deserialize(buffer, 1));
        final var in = mock(SerializableDataInputStream.class);
        assertThrows(MutabilityException.class, () -> immutable.deserialize(in, 1));
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var virtualVal = new EntityNumValue(2L);

        virtualVal.serialize(out);

        verify(out).writeLong(2L);
    }
}
