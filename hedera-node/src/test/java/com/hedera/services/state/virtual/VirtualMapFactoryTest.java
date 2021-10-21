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

import com.swirlds.jasperdb.VirtualInternalRecordSerializer;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class VirtualMapFactoryTest {
	private final String jdbDataLoc = "test/jdb";;
	private final testJdbFactory jdbFactory = new testJdbFactory();

	VirtualMapFactory factory;

	@BeforeEach
	void setUp() {
		factory = new VirtualMapFactory(jdbDataLoc, jdbFactory);
	}

	@Test
	void throwsAsExpected(){
		assertThrows(UncheckedIOException.class, () -> factory.newVirtualizedBlobs());
		assertThrows(UncheckedIOException.class, () -> factory.newVirtualizedStorage());
	}

	private static class testJdbFactory implements VirtualMapFactory.JasperDbFactory {

		@Override
		public <K extends VirtualKey, V extends VirtualValue> VirtualDataSource<K, V> newJdb(
				final VirtualLeafRecordSerializer<K, V> leafRecordSerializer,
				final VirtualInternalRecordSerializer virtualInternalRecordSerializer,
				final KeySerializer<K> keySerializer,
				final Path storageDir, final long maxNumOfKeys, final boolean mergingEnabled,
				final long internalHashesRamToDiskThreshold,
				final boolean preferDiskBasedIndexes) throws IOException {
			throw new IOException();
		}
	}
}
