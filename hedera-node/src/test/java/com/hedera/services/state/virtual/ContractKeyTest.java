package com.hedera.services.state.virtual;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ContractKeyTest {
	private final long contactNum = 1234L;
	private final long key = 123L;
	private final long otherKey = 124L;
	private final UInt256 uIntKey = UInt256.valueOf(key);
	private final byte[] key_array = uIntKey.toArray();

	private ContractKey subject;

	@Test
	void constructorsWork() {
		var testSubject1 = new ContractKey(contactNum, key);
		var testSubject2 = new ContractKey(contactNum, key_array);
		var testSubject3 = new ContractKey(contactNum,
				new int[] { 0, 0, 0, 0, 0, 0, (int) (key >> Integer.SIZE), (int) key });
		var testSubject4 =  new ContractKey(contactNum, otherKey);

		subject = new ContractKey();
		subject.setContractId(contactNum);
		subject.setKey(key);

		assertEquals(testSubject1, testSubject2);
		assertEquals(testSubject3, testSubject2);
		assertEquals(subject, testSubject3);
		assertEquals(subject, subject);
		assertNotEquals(subject, testSubject4);
		assertNotEquals(subject, null);
		assertArrayEquals(testSubject1.getKey(), testSubject2.getKey());
		assertEquals(testSubject2.getContractId(), testSubject3.getContractId());
		assertEquals(subject.toString(), testSubject1.toString());
		assertEquals(subject.getUint256Byte(0), testSubject2.getUint256Byte(0));
	}

	@Test
	void gettersWork() {
		subject = new ContractKey(contactNum, key_array);

		assertEquals(RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertEquals(MERKLE_VERSION, subject.getVersion());
		assertEquals(BigInteger.valueOf(key), subject.getKeyAsBigInteger());
	}

	@Test
	void serializeWorks() throws IOException {
		subject = new ContractKey(contactNum, key_array);

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
		subject = new ContractKey(contactNum, key_array);

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

		final var contractIdNonZeroBytes = subject.getContractIdNonZeroBytes();
		final var uint256KeyNonZeroBytes = subject.getUint256KeyNonZeroBytes();

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
		subject = new ContractKey(contactNum, key);
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
	void equalsUsingByteBufferWorks() throws IOException {
		subject = new ContractKey(contactNum, key);
		final var testSubject = new ContractKey(contactNum, key);
		final var bin = mock(ByteBuffer.class);

		given(bin.get())
				.willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
				.willReturn((byte) (subject.getContractId() >> 8))
				.willReturn((byte) (subject.getContractId()))
				.willReturn(subject.getUint256Byte(0));

		assertTrue(testSubject.equals(bin, 1));
	}

	@Test
	void readKeySizeWorks() {
		subject = new ContractKey(contactNum, key);
		final var contractIdNonZeroBytes = subject.getContractIdNonZeroBytes();
		final var uint256KeyNonZeroBytes = subject.getUint256KeyNonZeroBytes();
		final var bin = mock(ByteBuffer.class);

		given(bin.get())
				.willReturn(subject.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());

		assertEquals(1 + contractIdNonZeroBytes + uint256KeyNonZeroBytes, readKeySize(bin));
	}
}
