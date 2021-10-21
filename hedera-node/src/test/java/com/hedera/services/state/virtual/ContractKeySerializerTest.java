package com.hedera.services.state.virtual;

import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.DataFileCommon;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

import static com.hedera.services.state.virtual.ContractKeySerializer.DATA_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ContractKeySerializerTest {
	private final long contactNum = 1234L;
	private final long key = 123L;

	final ContractKeySerializer subject = new ContractKeySerializer();
	final ContractKey contractKey = new ContractKey(contactNum, key);

	@Test
	void gettersWork() {
		assertTrue(subject.isVariableSize());
		assertEquals(DATA_VERSION , subject.getCurrentDataVersion());
		assertEquals(DataFileCommon.VARIABLE_DATA_SIZE, subject.getSerializedSize());
		assertEquals(ContractKey.ESTIMATED_AVERAGE_SIZE, subject.getTypicalSerializedSize());
	}

	@Test
	void deserializerWorks() throws IOException {
		final var bin = mock(ByteBuffer.class);
		given(bin.get())
				.willReturn(contractKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes())
				.willReturn((byte) (contractKey.getContractId() >> 8))
				.willReturn((byte) (contractKey.getContractId()))
				.willReturn(contractKey.getUint256Byte(0));

		assertEquals(contractKey, subject.deserialize(bin, 1));
	}

	@Test
	void serializerWorks() throws IOException {
		final var contractIdNonZeroBytes = contractKey.getContractIdNonZeroBytes();
		final var uint256KeyNonZeroBytes = contractKey.getUint256KeyNonZeroBytes();

		final var out = mock(SerializableDataOutputStream.class);
		final var inOrder = inOrder(out);

		subject.serialize(contractKey, out);

		inOrder.verify(out).write(contractKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());
		for (int b = contractIdNonZeroBytes - 1; b >= 0; b--) {
			inOrder.verify(out).write((byte) (contractKey.getContractId() >> (b * 8)));
		}
		for (int b = uint256KeyNonZeroBytes - 1; b >= 0; b--) {
			inOrder.verify(out).write(contractKey.getUint256Byte(b));
		}
	}

	@Test
	void deserializeKeySizeWorks() {
		final var contractIdNonZeroBytes = contractKey.getContractIdNonZeroBytes();
		final var uint256KeyNonZeroBytes = contractKey.getUint256KeyNonZeroBytes();
		final var bin = mock(ByteBuffer.class);

		given(bin.get())
				.willReturn(contractKey.getContractIdNonZeroBytesAndUint256KeyNonZeroBytes());

		assertEquals(1 + contractIdNonZeroBytes + uint256KeyNonZeroBytes, subject.deserializeKeySize(bin));
	}
}
