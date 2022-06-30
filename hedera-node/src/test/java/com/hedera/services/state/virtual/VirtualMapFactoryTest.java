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
	private final ThrowingJdbFactoryBuilder jdbFactory = new ThrowingJdbFactoryBuilder();

	private VirtualMapFactory subject;

	@BeforeEach
	void setUp() {
		subject = new VirtualMapFactory(jdbFactory);
	}

	@Test
	void propagatesUncheckedFromBuilder() {
		assertThrows(UncheckedIOException.class, () -> subject.newVirtualizedBlobs());
		assertThrows(UncheckedIOException.class, () -> subject.newVirtualizedIterableStorage());
		assertThrows(UncheckedIOException.class, () -> subject.newScheduleListStorage());
		assertThrows(UncheckedIOException.class, () -> subject.newScheduleTemporalStorage());
		assertThrows(UncheckedIOException.class, () -> subject.newScheduleEqualityStorage());
	}

	private static class ThrowingJdbFactoryBuilder implements VirtualMapFactory.JasperDbBuilderFactory {
		@Override
		public <K extends VirtualKey<? super K>, V extends VirtualValue> JasperDbBuilder<K, V> newJdbBuilder() {
			throw new UncheckedIOException(new IOException("Oops!"));
		}
	}
}
