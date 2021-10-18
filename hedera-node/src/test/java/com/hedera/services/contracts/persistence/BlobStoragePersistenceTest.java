package com.hedera.services.contracts.persistence;

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

import com.hedera.services.state.merkle.MerkleContractStorageValue;
import com.hedera.services.state.merkle.internals.ContractStorageKey;
import com.hedera.services.utils.EntityIdUtils;
import com.swirlds.merkle.map.MerkleMap;
import org.ethereum.vm.DataWord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class BlobStoragePersistenceTest {
	final long contractNum = 75231L;
	final byte[] key = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
	final byte[] expectedValue = "012c45f789012c45f789012c45f78901".getBytes(StandardCharsets.UTF_8);

	private final byte[] address = EntityIdUtils.asSolidityAddress(0, 0, 75231L);
	private final byte[] addressStorage = "STUFF".getBytes();

	@Mock
	private Map<byte[], byte[]> storage;
	@Mock
	private MerkleMap<ContractStorageKey, MerkleContractStorageValue> contractStorage;

	BlobStoragePersistence subject;

	@BeforeEach
	private void setup() {
		subject = new BlobStoragePersistence(storage, () -> contractStorage);
	}

	@Test
	void delegatesExistence() {
		given(storage.containsKey(argThat((byte[] bytes) -> Arrays.equals(address, bytes)))).willReturn(true);

		// expect:
		assertTrue(subject.storageExist(address));
	}

	@Test
	void delegatesPersistence() {
		// when:
		subject.persist(address, addressStorage, 0, 0);

		// expect:
		verify(storage).put(
				argThat((byte[] bytes) -> Arrays.equals(address, bytes)),
				argThat((byte[] bytes) -> Arrays.equals(addressStorage, bytes)));
	}

	@Test
	void delegatesGet() {
		given(storage.get(argThat((byte[] bytes) -> Arrays.equals(address, bytes)))).willReturn(addressStorage);

		// when:
		byte[] actual = subject.get(address);

		// then:
		assertArrayEquals(addressStorage, actual);
	}

	@Test
	void sourceDelegatesGetAsExpected() {
		final var mapKey = new ContractStorageKey(contractNum, key);
		final var mapValue = new MerkleContractStorageValue(expectedValue);

		given(contractStorage.get(mapKey)).willReturn(mapValue);
		final var source = subject.scopedStorageFor(address);
		final var actualValue = source.get(DataWord.of(key));
		assertArrayEquals(expectedValue, actualValue.getData());

		Assertions.assertNull(source.get(DataWord.ZERO));
	}

	@Test
	void sourceDelegatesPutAsExpectedWithNoExtantMapping() {
		final var source = subject.scopedStorageFor(address);

		/* Without an existing leaf, creates a new one */
		final var captor = ArgumentCaptor.forClass(MerkleContractStorageValue.class);
		final var oneMapKey = new ContractStorageKey(contractNum, DataWord.ONE.getData());
		source.put(DataWord.ONE, DataWord.ONE);
		verify(contractStorage).put(eq(oneMapKey), captor.capture());
		assertArrayEquals(DataWord.ONE.getData(), captor.getValue().getValue());
	}

	@Test
	void sourceDelegatesPutToG4mWithExtantMapping() {
		final var mapKey = new ContractStorageKey(contractNum, key);
		final var mapValue = mock(MerkleContractStorageValue.class);
		given(contractStorage.containsKey(mapKey)).willReturn(true);
		given(contractStorage.getForModify(mapKey)).willReturn(mapValue);

		final var source = subject.scopedStorageFor(address);

		/* Without an existing leaf, creates a new one */
		source.put(DataWord.of(key), DataWord.of(expectedValue));
		verify(mapValue).setValue(expectedValue);
	}

	@Test
	void sourceDelegatesDeleteToRemove() {
		final var mapKey = new ContractStorageKey(contractNum, key);

		final var source = subject.scopedStorageFor(address);

		source.delete(DataWord.of(key));
		verify(contractStorage).remove(mapKey);
	}

	@Test
	void sourceDoesntFlush() {
		final var source = subject.scopedStorageFor(address);

		assertFalse(source.flush());
	}
}
