package com.hedera.services.state.virtual;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import static com.hedera.services.state.virtual.ContractValue.MERKLE_VERSION;
import static com.hedera.services.state.virtual.ContractValue.RUNTIME_CONSTRUCTABLE_ID;
import static com.hedera.services.state.virtual.ContractValue.SERIALIZED_SIZE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


class ContractValueTest {
	private final UInt256 uint256Value = UInt256.fromHexString("0x5c504ed432cb51138bcf09aa5e8a410dd4a1e204ef84bfed1be16dfba1b22060");
	private final UInt256 otherUint256Value = UInt256.fromHexString("0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563");
	private final byte[] bytesValue = uint256Value.toArray();
	private final byte[] otherBytesValue = otherUint256Value.toArray();
	private final byte[] defaultEmpty = new byte[SERIALIZED_SIZE];

	private ContractValue subject;

	@BeforeEach
	void setup() {
		subject = new ContractValue(bytesValue);
	}

	@Test
	void gettersWork() {
		// then:
		assertEquals(RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertEquals(MERKLE_VERSION, subject.getVersion());
		assertEquals(bytesValue, subject.getValue());
		assertEquals(uint256Value.toBigInteger(), subject.asBigInteger());
	}

	@Test
	void equalsWork() {
		// given:
		final var testSubject1 = new ContractValue(bytesValue);
		final var testSubject2 = new ContractValue(otherBytesValue);
		final var testSubject3 = new ContractValue(uint256Value.toBigInteger());
		final var testSubject4 = new ContractValue();
		testSubject4.setValue(bytesValue);

		// then:
		assertEquals(testSubject1, testSubject3);
		assertEquals(testSubject1, testSubject4);
		assertEquals(testSubject1, subject);
		assertEquals(testSubject1.hashCode(), subject.hashCode());
		// and:
		assertNotEquals(testSubject2, subject);
		assertNotEquals(null, subject);
		assertNotEquals(testSubject2.hashCode(), subject.hashCode());
		// and:
		assertEquals(testSubject1.asBigInteger(), subject.asBigInteger());
		assertEquals(testSubject1.getValue(), subject.getValue());
		// and:
		assertEquals(testSubject1.getVersion(), subject.getVersion());
		assertEquals(testSubject1.getClassId(), subject.getClassId());
		// and:
		assertEquals(testSubject1.toString(), subject.toString());

		// forcing equals on null and objects of different class
		var forcedEquals = subject.equals(null);
		assertFalse(forcedEquals);

		forcedEquals = subject.equals(otherBytesValue);
		assertFalse(forcedEquals);
	}

	@Test
	void copyWorks() {
		// when:
		final var copySubject = subject.copy();

		// then:
		assertNotSame(copySubject, subject);
		assertEquals(subject, copySubject);
		// and:
		assertTrue(subject.isImmutable());
		assertTrue(copySubject.isImmutable());
	}

	@Test
	void setsLongValue() {
		// given:
		final var LONG_VALUE = 5L;
		final var expected = new ContractValue(LONG_VALUE);
		// when:
		subject.setValue(LONG_VALUE);

		// then:
		assertEquals(expected, subject);
		// and:
		assertEquals(LONG_VALUE, subject.asLong());
	}

	@Test
	void setsShorterBigInt() {
		// given:
		final var address = UInt256.fromHexString(Address.ZERO.toHexString());
		final var bytesAddress = address.toArray();

		// when:
		subject.setValue(address.toBigInteger());

		// then:
		assertArrayEquals(bytesAddress, subject.getValue());
	}

	@Test
	void setsLongerBigInt() {
		final var len = 33;
		final int value = 123;
		byte[] bigIntegerBytes = new byte[len];
		bigIntegerBytes[0] = (byte) value;
		bigIntegerBytes[len-1] = (byte) value;

		subject.setValue(new BigInteger(bigIntegerBytes));

		final var actual = subject.getValue();
		var actualLen = 31;
		for (int i = len-1; i >= len-32; i--) {
			assertEquals(bigIntegerBytes[i], actual[actualLen--], "byte at index " + i + " dont match");
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
		// when:
		final var readOnly = subject.asReadOnly();
		final var bigIntValue = uint256Value.toBigInteger();

		// then:
		assertThrows(IllegalStateException.class, () -> readOnly.setValue(bytesValue));
		assertThrows(IllegalStateException.class, () -> readOnly.setValue(bigIntValue));
		assertThrows(IllegalStateException.class, () -> readOnly.setValue(1));
	}

	@Test
	void serializeWorks() throws IOException {
		// given:
		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).write(subject.getValue());
	}

	@Test
	void serializeUsingByteBufferWorks() throws IOException {
		// given:
		final var out = mock(ByteBuffer.class);
		final var inOrder = inOrder(out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(out).put(subject.getValue());
	}

	@Test
	void deserializeWorks() throws IOException {
		// given:
		subject = new ContractValue();
		final var in = mock(SerializableDataInputStream.class);
		doAnswer(invocation -> {
			subject.setValue(bytesValue);
			return SERIALIZED_SIZE;
		})
				.when(in).read(subject.getValue());

		// when:
		subject.deserialize(in, MERKLE_VERSION);

		// then:
		assertEquals(bytesValue, subject.getValue());
		// and:
		verify(in).read(defaultEmpty);
	}

	@Test
	void deserializeThrowsOnInvalidLength() throws IOException{
		// given:
		final var in = mock(SerializableDataInputStream.class);
		given(in.read()).willReturn(0);

		// then:
		assertThrows(AssertionError.class, () -> subject.deserialize(in, MERKLE_VERSION));
	}

	@Test
	void deserializeWithByteBufferWorks() throws IOException {
		// given:
		subject = new ContractValue();
		final var byteBuffer = mock(ByteBuffer.class);
		doAnswer(invocation -> {
			subject.setValue(bytesValue);
			return null;
		})
				.when(byteBuffer).get(subject.getValue());

		// when:
		subject.deserialize(byteBuffer, MERKLE_VERSION);

		// then:
		assertEquals(bytesValue, subject.getValue());
		// and:
		verify(byteBuffer).get(defaultEmpty);
	}

	@Test
	void cannotDeserializeIntoAReadOnlyContractValue() throws IOException {
		final var readOnly = subject.asReadOnly();

		final var in = mock(SerializableDataInputStream.class);
		doAnswer(invocation -> {
			subject.setValue(bytesValue);
			return SERIALIZED_SIZE;
		}).when(in).read(subject.getValue());

		assertThrows(IllegalStateException.class, () -> readOnly.deserialize(in, MERKLE_VERSION));

		// and when
		final var byteBuffer = mock(ByteBuffer.class);
		doAnswer(invocation -> {
			subject.setValue(bytesValue);
			return null;
		}).when(byteBuffer).get(subject.getValue());

		assertThrows(IllegalStateException.class, () -> readOnly.deserialize(byteBuffer, MERKLE_VERSION));
	}
}
