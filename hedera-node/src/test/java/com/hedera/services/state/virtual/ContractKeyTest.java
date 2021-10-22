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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;

import static com.hedera.services.state.virtual.ContractKey.MERKLE_VERSION;
import static com.hedera.services.state.virtual.ContractKey.RUNTIME_CONSTRUCTABLE_ID;
import static com.hedera.services.state.virtual.ContractKey.readKeySize;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ContractKeyTest {
	private final long contractNum = 1234L;
	private final long key = 123L;
	private final long otherContractNum = 1235L;
	private final long otherKey = 124L;
	private final UInt256 largeKey = UInt256.fromHexString("0x290decd9548b62a8d60345a988386fc84ba6bc95484008f6362f93160ef3e563");
	private final UInt256 uIntKey = UInt256.valueOf(key);
	private final byte[] key_array = uIntKey.toArray();

	private ContractKey subject;

	@Test
	void equalsWork() {
		var testSubject1 = new ContractKey(contractNum, key);
		var testSubject2 = new ContractKey(contractNum, key_array);
		var testSubject3 = new ContractKey(contractNum,
				new int[] { 0, 0, 0, 0, 0, 0, (int) (key >> Integer.SIZE), (int) key });
		var testSubject4 =  new ContractKey(contractNum, otherKey);
		var testSubject5 =  new ContractKey(otherContractNum, key);

		subject = new ContractKey();
		subject.setContractId(contractNum);
		subject.setKey(key);

		assertEquals(testSubject1, testSubject2);
		assertEquals(testSubject3, testSubject2);
		assertEquals(subject, testSubject3);
		assertEquals(subject, subject);
		assertNotEquals(subject, testSubject4);
		assertNotEquals(null, subject);
		assertNotEquals(subject, key);
		assertNotEquals(subject, testSubject5);
		assertArrayEquals(testSubject1.getKey(), testSubject2.getKey());
		assertEquals(testSubject2.getContractId(), testSubject3.getContractId());
		assertEquals(subject.toString(), testSubject1.toString());
		assertEquals(subject.getUint256Byte(0), testSubject2.getUint256Byte(0));
		var forcedEqualsCheck = subject.equals(key);
		assertFalse(forcedEqualsCheck, "forcing equals on two different class types.");
	}

	@Test
	void gettersWork() {
		subject = new ContractKey(contractNum, key_array);

		assertEquals(RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertEquals(MERKLE_VERSION, subject.getVersion());
		assertEquals(BigInteger.valueOf(key), subject.getKeyAsBigInteger());
	}

	@Test
	void serializeWorks() throws IOException {
		subject = new ContractKey(contractNum, key_array);

		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		final var contractIdNonZeroBytes = subject.getContractIdNonZeroBytes();
		final var uint256KeyNonZeroBytes = subject.getUint256KeyNonZeroBytes();

		subject.serialize(out);

		inOrder.verify(out).write(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
		for (int b = contractIdNonZeroBytes - 1; b >= 0; b--) {
			inOrder.verify(out).write((byte) (subject.getContractId() >> (b * 8)));
		}
		for (int b = uint256KeyNonZeroBytes - 1; b >= 0; b--) {
			inOrder.verify(out).write(subject.getUint256Byte(b));
		}
	}

	@Test
	void serializeUsingByteBufferWorks() throws IOException {
		subject = new ContractKey(contractNum, key_array);

		final var out = mock(ByteBuffer.class);
		final var inOrder = inOrder(out);

		final var contractIdNonZeroBytes = subject.getContractIdNonZeroBytes();
		final var uint256KeyNonZeroBytes = subject.getUint256KeyNonZeroBytes();

		subject.serialize(out);
		inOrder.verify(out).put(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
		for (int b = contractIdNonZeroBytes - 1; b >= 0; b--) {
			inOrder.verify(out).put((byte) (subject.getContractId() >> (b * 8)));
		}
		for (int b = uint256KeyNonZeroBytes - 1; b >= 0; b--) {
			inOrder.verify(out).put(subject.getUint256Byte(b));
		}
	}

	@Test
	void deserializeWorks() throws IOException {
		subject = new ContractKey(Long.MAX_VALUE, key);
		final var testSubject = new ContractKey();

		final var fin = mock(SerializableDataInputStream.class);
		given(fin.readByte())
				.willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
				.willReturn((byte) (subject.getContractId() >> 56))
				.willReturn((byte) (subject.getContractId() >> 48))
				.willReturn((byte) (subject.getContractId() >> 40))
				.willReturn((byte) (subject.getContractId() >> 32))
				.willReturn((byte) (subject.getContractId() >> 24))
				.willReturn((byte) (subject.getContractId() >> 16))
				.willReturn((byte) (subject.getContractId() >> 8))
				.willReturn((byte) (subject.getContractId()))
				.willReturn(subject.getUint256Byte(0));

		testSubject.deserialize(fin, 1);

		assertEquals(subject, testSubject);
	}

	@Test
	void deserializeWithByteBufferWorks() throws IOException {
		subject = new ContractKey(contractNum, key);
		final var testSubject = new ContractKey();

		final var bin = mock(ByteBuffer.class);
		given(bin.get())
				.willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
				.willReturn((byte) (subject.getContractId() >> 8))
				.willReturn((byte) (subject.getContractId()))
				.willReturn(subject.getUint256Byte(0));

		testSubject.deserialize(bin, 1);

		assertEquals(subject, testSubject);
	}

	@Test
	void deserializeLargeKeyWorks() throws IOException {
		subject = new ContractKey(contractNum, largeKey.toArray());

		final var fin = mock(SerializableDataInputStream.class);
		given(fin.readByte())
				.willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
				.willReturn((byte) (subject.getContractId() >> 8))
				.willReturn((byte) (subject.getContractId()))
				.willReturn(subject.getUint256Byte(31), subject.getUint256Byte(30), subject.getUint256Byte(29),
						subject.getUint256Byte(28), subject.getUint256Byte(27), subject.getUint256Byte(26),
						subject.getUint256Byte(25), subject.getUint256Byte(24), subject.getUint256Byte(23),
						subject.getUint256Byte(22), subject.getUint256Byte(21), subject.getUint256Byte(20),
						subject.getUint256Byte(19), subject.getUint256Byte(18), subject.getUint256Byte(17),
						subject.getUint256Byte(16), subject.getUint256Byte(15), subject.getUint256Byte(14),
						subject.getUint256Byte(13), subject.getUint256Byte(12), subject.getUint256Byte(11),
						subject.getUint256Byte(10), subject.getUint256Byte(9), subject.getUint256Byte(8),
						subject.getUint256Byte(7), subject.getUint256Byte(6), subject.getUint256Byte(5),
						subject.getUint256Byte(4), subject.getUint256Byte(3), subject.getUint256Byte(2),
						subject.getUint256Byte(1), subject.getUint256Byte(0));

		final var testSubject = new ContractKey();
		testSubject.deserialize(fin, 1);

		assertEquals(subject, testSubject);
	}

	@Test
	void equalsUsingByteBufferWorks() throws IOException {
		subject = new ContractKey(0L, key);
		final var testSubject = new ContractKey(0L, key);
		final var bin = mock(ByteBuffer.class);

		given(bin.get())
				.willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
				.willReturn((byte) (subject.getContractId()))
				.willReturn(subject.getUint256Byte(0));

		assertTrue(testSubject.equals(bin, 1));
	}

	@Test
	void equalsUsingByteBufferFailsAsExpected() throws IOException {
		subject = new ContractKey(contractNum, key);
		final var testSubject1 = new ContractKey(Long.MAX_VALUE, key);
		final var testSubject2 = new ContractKey(contractNum, largeKey.toArray());
		final var testSubject3 = new ContractKey(otherContractNum, key);
		final var bin = mock(ByteBuffer.class);

		given(bin.get())
				.willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
				.willReturn((byte) (subject.getContractId() >> 8))
				.willReturn((byte) (subject.getContractId()))
				.willReturn(subject.getUint256Byte(0));
		assertFalse(testSubject1.equals(bin, 1));

		given(bin.get())
				.willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
				.willReturn((byte) (subject.getContractId() >> 8))
				.willReturn((byte) (subject.getContractId()))
				.willReturn(subject.getUint256Byte(0));
		assertFalse(testSubject2.equals(bin, 1));

		given(bin.get())
				.willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
				.willReturn((byte) (subject.getContractId() >> 8))
				.willReturn((byte) (subject.getContractId()))
				.willReturn(subject.getUint256Byte(0));
		assertFalse(testSubject3.equals(bin, 1));
	}

	@Test
	void readKeySizeWorks() {
		subject = new ContractKey(contractNum, key);
		final var contractIdNonZeroBytes = subject.getContractIdNonZeroBytes();
		final var uint256KeyNonZeroBytes = subject.getUint256KeyNonZeroBytes();
		final var bin = mock(ByteBuffer.class);

		given(bin.get())
				.willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());

		assertEquals(1 + contractIdNonZeroBytes + uint256KeyNonZeroBytes, readKeySize(bin));
	}

	@Test
	void calculatesNonZeroBytesCorrectly() {
		subject = new ContractKey(0, 0);

		assertEquals(1, subject.getContractIdNonZeroBytes());
		assertEquals(1, subject.getUint256KeyNonZeroBytes());
	}

	@Test
	void cannotUseInvalidKeys() {
		final var notEight = 7;
		final var not32 = 41;
		final int[] intArr = new int[notEight];
		final byte[] byteArr = new byte[not32];

		subject = new ContractKey();

		assertThrows(IllegalArgumentException.class, () -> new ContractKey(contractNum, (byte[]) null));
		assertThrows(IllegalArgumentException.class, () -> new ContractKey(contractNum, byteArr));
		assertThrows(IllegalArgumentException.class, () -> subject.setKey(null));
		assertThrows(IllegalArgumentException.class, () -> new ContractKey(contractNum, intArr));
	}

	@Test
	void toStringWorks() {
		subject = new ContractKey(contractNum, key);
		final var subjectDescription = "ContractKey{id=1234(4D2), key=123(0,0,0,0,0,0,0,7B)}";

		assertEquals(subjectDescription, subject.toString());
	}
}
