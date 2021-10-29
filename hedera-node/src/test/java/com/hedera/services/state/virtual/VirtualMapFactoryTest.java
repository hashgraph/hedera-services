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

import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class VirtualMapFactoryTest {
	private final String jdbDataLoc = "test/jdb";
	;
	private final ThrowingJdbFactoryBuilder jdbFactory = new ThrowingJdbFactoryBuilder();

	VirtualMapFactory factory;

	@BeforeEach
	void setUp() {
		factory = new VirtualMapFactory(jdbDataLoc, jdbFactory);
	}

	@Test
	void propagatesUncheckedFromBuilder() {
		assertThrows(UncheckedIOException.class, () -> factory.newVirtualizedBlobs());
		assertThrows(UncheckedIOException.class, () -> factory.newVirtualizedStorage());
	}

	private static class ThrowingJdbFactoryBuilder implements VirtualMapFactory.JasperDbBuilderFactory {
		@Override
		public <K extends VirtualKey, V extends VirtualValue> JasperDbBuilder<K, V> newJdbBuilder() {
			throw new UncheckedIOException(new IOException("Oops!"));
		}
	}
}
