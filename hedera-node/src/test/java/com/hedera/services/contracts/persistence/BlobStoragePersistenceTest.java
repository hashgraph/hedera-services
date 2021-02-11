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

import com.hedera.services.utils.EntityIdUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

class BlobStoragePersistenceTest {
	byte[] address = EntityIdUtils.asSolidityAddress(0, 0, 13257);
	byte[] addressStorage = "STUFF".getBytes();

	Map<byte[], byte[]> storage;

	BlobStoragePersistence subject;

	@BeforeEach
	private void setup() {
		storage = mock(Map.class);

		subject = new BlobStoragePersistence(storage);
	}

	@Test
	public void delegatesExistence() {
		given(storage.containsKey(argThat((byte[] bytes) -> Arrays.equals(address, bytes)))).willReturn(true);

		// expect:
		assertTrue(subject.storageExist(address));
	}

	@Test
	public void delegatesPersistence() {
		// when:
		subject.persist(address, addressStorage, 0, 0);

		// expect:
		verify(storage).put(
				argThat((byte[] bytes) -> Arrays.equals(address, bytes)),
				argThat((byte[] bytes) -> Arrays.equals(addressStorage, bytes)));
	}

	@Test
	public void delegatesGet() {
		given(storage.get(argThat((byte[] bytes) -> Arrays.equals(address, bytes)))).willReturn(addressStorage);

		// when:
		byte[] actual = subject.get(address);

		// then:
		assertArrayEquals(addressStorage, actual);
	}
}
