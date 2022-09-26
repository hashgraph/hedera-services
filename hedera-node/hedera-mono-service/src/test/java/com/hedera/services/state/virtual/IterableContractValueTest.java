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
package com.hedera.services.state.virtual;

import static com.hedera.services.state.virtual.IterableContractValue.ITERABLE_VERSION;
import static com.hedera.services.state.virtual.IterableContractValue.NON_ITERABLE_SERIALIZED_SIZE;
import static com.hedera.services.state.virtual.IterableContractValue.RUNTIME_CONSTRUCTABLE_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IterableContractValueTest {
    private static final UInt256 prevUint256Key =
            UInt256.fromHexString(
                    "0x00304ed432ce31138ecf09aa3e8a410dd4a1e204ef84efed1ee16dfea1e22060");
    private static final byte numNonZeroBytesInPrev = 31;
    private static final int[] explicitPrevKey = ContractKey.asPackedInts(prevUint256Key);
    private static final UInt256 nextUint256Key =
            UInt256.fromHexString(
                    "0x0000fe0432ce31138ecf09aa3e8a410004a1e204ef84efe01ee160fea1e22060");
    private static final byte numNonZeroBytesInNext = 30;
    private static final int[] explicitNextKey = ContractKey.asPackedInts(nextUint256Key);
    private static final UInt256 otherUint256Key =
            UInt256.fromHexString(
                    "0x1111fe0432ce31138ecf09aa3e8a410004bbe204ef84efe01ee160febbe22060");
    private static final int[] explicitOtherKey = ContractKey.asPackedInts(otherUint256Key);
    private static final UInt256 uint256Value =
            UInt256.fromHexString(
                    "0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060");
    private static final long scopedContractId = 1_234L;
    private static final byte[] bytesValue = uint256Value.toArray();
    private static final byte[] defaultEmpty = new byte[NON_ITERABLE_SERIALIZED_SIZE];
    private static final ContractKey prevKey =
            new ContractKey(scopedContractId, prevUint256Key.toArray());
    private static final ContractKey nextKey =
            new ContractKey(scopedContractId, nextUint256Key.toArray());

    private IterableContractValue subject;

    @BeforeEach
    void setup() {
        subject = new IterableContractValue(bytesValue, explicitPrevKey, explicitNextKey);
    }

    @Test
    void keyGettersSettersWork() {
        subject = new IterableContractValue(bytesValue);

        subject.setPrevKey(explicitPrevKey);
        subject.setNextKey(explicitNextKey);

        assertEquals(prevKey, subject.getPrevKeyScopedTo(scopedContractId));
        assertEquals(nextKey, subject.getNextKeyScopedTo(scopedContractId));

        assertEquals(numNonZeroBytesInPrev, subject.getPrevUint256KeyNonZeroBytes());
        assertEquals(numNonZeroBytesInNext, subject.getNextUint256KeyNonZeroBytes());

        subject.markAsRootMapping();
        assertNull(subject.getPrevKeyScopedTo(scopedContractId));

        subject.markAsLastMapping();
        assertNull(subject.getNextKeyScopedTo(scopedContractId));
    }

    @Test
    void gettersWork() {
        assertEquals(RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
        assertEquals(ITERABLE_VERSION, subject.getVersion());
        assertEquals(bytesValue, subject.getValue());
        assertEquals(uint256Value.toBigInteger(), subject.asBigInteger());
    }

    @Test
    void objectContractMet() {
        final var sameButDifferent =
                new IterableContractValue(bytesValue, explicitPrevKey, explicitNextKey);
        assertEquals(subject, sameButDifferent);
        assertEquals(subject.hashCode(), sameButDifferent.hashCode());

        var differentPrevKey = new IterableContractValue(bytesValue, null, explicitNextKey);
        assertNotEquals(subject, differentPrevKey);
        assertNotEquals(subject.hashCode(), differentPrevKey.hashCode());
        differentPrevKey = new IterableContractValue(bytesValue, explicitOtherKey, explicitNextKey);
        assertNotEquals(subject, differentPrevKey);
        assertNotEquals(subject.hashCode(), differentPrevKey.hashCode());

        var differentNextKey = new IterableContractValue(bytesValue, explicitPrevKey, null);
        assertNotEquals(subject, differentNextKey);
        assertNotEquals(subject.hashCode(), differentNextKey.hashCode());
        differentNextKey = new IterableContractValue(bytesValue, explicitPrevKey, explicitOtherKey);
        assertNotEquals(subject, differentNextKey);
        assertNotEquals(subject.hashCode(), differentNextKey.hashCode());
    }

    @Test
    void toStringAsExpected() {
        final var desired =
                "ContractValue"
                    + "{41754673891915775801010071770256094221237405171466406054945132944954670325856(5C"
                    + " 50 4E D4 32 CB 51 13 8B CF 09 AA 5E 8A 41 0D D4 A1 E2 04 EF 84 BF ED 1B E1"
                    + " 6D FB A1 B2 20 60 ),"
                    + " prevKey=304ed432ce31138ecf09aa3e8a410dd4a1e204ef84efed1ee16dfea1e22060, "
                    + "nextKey=fe0432ce31138ecf09aa3e8a41004a1e204ef84efe01ee160fea1e22060}";

        assertEquals(desired, subject.toString());
    }

    @Test
    void hasExpectedVersion() {
        assertEquals(IterableContractValue.ITERABLE_VERSION, subject.getVersion());
    }

    @Test
    void copyWorks() {
        final var copySubject = subject.copy();

        assertNotSame(copySubject, subject);
        assertEquals(subject, copySubject);

        assertTrue(subject.isImmutable());
        assertFalse(copySubject.isImmutable());
    }

    @Test
    void setsLongValue() {
        final var LONG_VALUE = 5L;
        subject = new IterableContractValue();
        final var expected = new IterableContractValue(LONG_VALUE);

        subject.setValue(LONG_VALUE);

        assertEquals(expected, subject);
        assertEquals(LONG_VALUE, subject.asLong());
    }

    @Test
    void setsShorterBigInt() {
        final var address = UInt256.fromHexString(Address.ZERO.toHexString());
        final var bytesAddress = address.toArray();

        subject.setValue(address.toBigInteger());

        assertArrayEquals(bytesAddress, subject.getValue());
    }

    @Test
    void setsLongerBigInt() {
        final var len = 33;
        final int value = 123;
        final byte[] bigIntegerBytes = new byte[len];
        bigIntegerBytes[0] = (byte) value;
        bigIntegerBytes[len - 1] = (byte) value;

        subject.setValue(new BigInteger(bigIntegerBytes));

        final var actual = subject.getValue();
        var actualLen = 31;
        for (int i = len - 1; i >= len - 32; i--) {
            assertEquals(
                    bigIntegerBytes[i], actual[actualLen--], "byte at index " + i + " dont match");
        }

        assertEquals(BigInteger.valueOf(value), new BigInteger(subject.getValue()));
    }

    @Test
    void setterFailsOnInvalidBytesLength() {
        final var invalidValue = "test".getBytes();
        assertThrows(IllegalArgumentException.class, () -> subject.setValue(invalidValue));
    }

    @Test
    void setThrowsOnReadOnly() {
        final var readOnly = subject.asReadOnly();
        final var bigIntValue = uint256Value.toBigInteger();

        assertThrows(IllegalStateException.class, () -> readOnly.setValue(bytesValue));
        assertThrows(IllegalStateException.class, () -> readOnly.setValue(bigIntValue));
        assertThrows(IllegalStateException.class, () -> readOnly.setValue(1));
    }

    @Test
    void serializeWorksWithTwoKeys() throws IOException {
        final var rawPrevKey = prevUint256Key.toArrayUnsafe();
        final var rawNextKey = nextUint256Key.toArrayUnsafe();
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).write(subject.getValue());
        inOrder.verify(out).write(numNonZeroBytesInPrev);
        for (int i = 0, offset = 32 - numNonZeroBytesInPrev; i < numNonZeroBytesInPrev; i++) {
            inOrder.verify(out).write(rawPrevKey[i + offset]);
        }
        inOrder.verify(out).write(numNonZeroBytesInNext);
        for (int i = 0, offset = 32 - numNonZeroBytesInNext; i < numNonZeroBytesInNext; i++) {
            inOrder.verify(out).write(rawNextKey[i + offset]);
        }
    }

    @Test
    void serializeWorksWithNoKeys() throws IOException {
        final var out = mock(SerializableDataOutputStream.class);
        final var inOrder = inOrder(out);

        subject = new IterableContractValue(bytesValue);

        subject.serialize(out);

        inOrder.verify(out).write(subject.getValue());
        inOrder.verify(out, times(2)).write(-1);
    }

    @Test
    void serializeUsingByteBufferWorksWithBothKeys() throws IOException {
        final var rawPrevKey = prevUint256Key.toArrayUnsafe();
        final var rawNextKey = nextUint256Key.toArrayUnsafe();

        final var out = mock(ByteBuffer.class);
        final var inOrder = inOrder(out);

        subject.serialize(out);

        inOrder.verify(out).put(subject.getValue());
        inOrder.verify(out).put(numNonZeroBytesInPrev);
        for (int i = 0, offset = 32 - numNonZeroBytesInPrev; i < numNonZeroBytesInPrev; i++) {
            inOrder.verify(out).put(rawPrevKey[i + offset]);
        }
        inOrder.verify(out).put(numNonZeroBytesInNext);
        for (int i = 0, offset = 32 - numNonZeroBytesInNext; i < numNonZeroBytesInNext; i++) {
            inOrder.verify(out).put(rawNextKey[i + offset]);
        }
    }

    @Test
    void deserializeWorksForV1() throws IOException {
        subject = new IterableContractValue();
        final var in = mock(SerializableDataInputStream.class);
        doAnswer(
                        invocation -> {
                            subject.setValue(bytesValue);
                            return NON_ITERABLE_SERIALIZED_SIZE;
                        })
                .when(in)
                .read(subject.getValue());

        subject.deserialize(in, ITERABLE_VERSION);

        assertEquals(bytesValue, subject.getValue());
        verify(in).read(defaultEmpty);
    }

    @Test
    void deserializeWorksForV2WithBothKeys() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        subject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new IterableContractValue();
        newSubject.deserialize(din, IterableContractValue.ITERABLE_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void deserializeWorksForV2WithNoKeys() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        subject = new IterableContractValue(bytesValue);
        subject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new IterableContractValue();
        newSubject.deserialize(din, IterableContractValue.ITERABLE_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void deserializeWorksForV2WithOnlyPrevKey() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        subject = new IterableContractValue(bytesValue);
        subject.setPrevKey(explicitPrevKey);
        subject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new IterableContractValue();
        newSubject.deserialize(din, IterableContractValue.ITERABLE_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void deserializeWorksForV2WithOnlyNext() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final var dos = new SerializableDataOutputStream(baos);
        subject = new IterableContractValue(bytesValue);
        subject.setNextKey(explicitNextKey);
        subject.serialize(dos);
        dos.flush();
        final var bytes = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        final var din = new SerializableDataInputStream(bais);

        final var newSubject = new IterableContractValue();
        newSubject.deserialize(din, IterableContractValue.ITERABLE_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void deserializeThrowsOnInvalidLength() throws IOException {
        final var in = mock(SerializableDataInputStream.class);
        given(in.read()).willReturn(0);

        assertThrows(AssertionError.class, () -> subject.deserialize(in, ITERABLE_VERSION));
    }

    @Test
    void deserializeWithByteBufferWorks() throws IOException {
        subject = new IterableContractValue();
        final var byteBuffer = mock(ByteBuffer.class);
        doAnswer(
                        invocation -> {
                            subject.setValue(bytesValue);
                            return null;
                        })
                .when(byteBuffer)
                .get(subject.getValue());

        subject.deserialize(byteBuffer, ITERABLE_VERSION);

        assertEquals(bytesValue, subject.getValue());
        verify(byteBuffer).get(defaultEmpty);
    }

    @Test
    void deserializeWithByteBufferWorksForV2WithBothKeys() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate(256);
        buffer.mark();
        subject.serialize(buffer);
        buffer.reset();

        final var newSubject = new IterableContractValue();
        newSubject.deserialize(buffer, ITERABLE_VERSION);

        assertEquals(subject, newSubject);
    }

    @Test
    void cannotDeserializeIntoAReadOnlyContractValue() throws IOException {
        final var readOnly = subject.asReadOnly();

        final var in = mock(SerializableDataInputStream.class);
        doAnswer(
                        invocation -> {
                            subject.setValue(bytesValue);
                            return NON_ITERABLE_SERIALIZED_SIZE;
                        })
                .when(in)
                .read(subject.getValue());

        assertThrows(IllegalStateException.class, () -> readOnly.deserialize(in, ITERABLE_VERSION));

        // and when
        final var byteBuffer = mock(ByteBuffer.class);
        doAnswer(
                        invocation -> {
                            subject.setValue(bytesValue);
                            return null;
                        })
                .when(byteBuffer)
                .get(subject.getValue());

        assertThrows(
                IllegalStateException.class,
                () -> readOnly.deserialize(byteBuffer, ITERABLE_VERSION));
    }
}
