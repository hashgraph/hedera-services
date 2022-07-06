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
package com.hedera.services.state.virtual;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class VirtualBlobValueTest {
    private VirtualBlobValue subject;
    private byte[] data = "data".getBytes();
    private byte[] otherData = "someOtherData".getBytes();

    @BeforeEach
    void setup() {
        subject = new VirtualBlobValue(data);
    }

    @Test
    void objectContractMet() {
        final var one = new VirtualBlobValue(data);
        final var two = new VirtualBlobValue(otherData);
        final var three = new VirtualBlobValue(data);
        final var twoRef = two;

        assertNotEquals(two, one);
        assertEquals(two, twoRef);
        assertEquals(one, three);

        assertNotEquals(one.hashCode(), two.hashCode());
        assertEquals(two.hashCode(), twoRef.hashCode());
        assertEquals(one.hashCode(), three.hashCode());
        assertDoesNotThrow(subject::release);

        final var forcedEqualsCheck = one.equals(data);
        assertFalse(forcedEqualsCheck, "forcing equals on two different class types.");
    }

    @Test
    void serializeWorks() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);
        subject.serialize(out);

        inOrder.verify(out).writeByteArray(data);
    }

    @Test
    void deserializeWorks() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        final var defaultSubject = new VirtualBlobValue();
        given(in.readByteArray(Integer.MAX_VALUE)).willReturn(data);

        defaultSubject.deserialize(in, VirtualBlobValue.CURRENT_VERSION);

        assertEquals(subject, defaultSubject);
    }

    @Test
    void serializeWithByteBufferWorks() throws IOException {
        final var buffer = mock(ByteBuffer.class);
        final var inOrder = inOrder(buffer);
        subject.serialize(buffer);

        inOrder.verify(buffer).putInt(data.length);
        inOrder.verify(buffer).put(data);
    }

    @Test
    void deserializeWithByteBufferWorks() throws IOException {
        final var buffer = mock(ByteBuffer.class);
        final var defaultSubject = new VirtualBlobValue();
        int len = data.length;

        given(buffer.getInt()).willReturn(len);
        doAnswer(
                        new Answer() {
                            @Override
                            public Object answer(final InvocationOnMock invocationOnMock) {
                                defaultSubject.setData(data);
                                return null;
                            }
                        })
                .when(buffer)
                .get(ArgumentMatchers.any());

        defaultSubject.deserialize(buffer, VirtualBlobValue.CURRENT_VERSION);

        assertEquals(subject, defaultSubject);
    }

    @Test
    void merkleMethodsWork() {
        assertEquals(VirtualBlobValue.CURRENT_VERSION, subject.getVersion());
        assertEquals(VirtualBlobValue.CLASS_ID, subject.getClassId());
    }

    @Test
    void gettersAndSettersWork() {
        VirtualBlobValue value = new VirtualBlobValue();
        value.setData(otherData);
        assertArrayEquals(otherData, value.getData());
        assertEquals(DataFileCommon.VARIABLE_DATA_SIZE, value.sizeInBytes());
    }

    @Test
    void toStringWorks() {
        assertEquals("VirtualBlobValue{data=[100, 97, 116, 97]}", subject.toString());
    }
}
