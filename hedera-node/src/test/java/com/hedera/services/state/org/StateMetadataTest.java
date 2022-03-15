package com.hedera.services.state.org;

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

import com.hedera.services.ServicesApp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;

@ExtendWith(MockitoExtension.class)
class StateMetadataTest {
	@Mock
	private ServicesApp app;

	private StateMetadata subject;

	@BeforeEach
	void setUp() {
		subject = new StateMetadata(app);
	}

	@Test
	void copyAsExpected() {
		// when:
		final var copy = subject.copy();

		// then:
		assertSame(app, copy.app());
	}

	@Test
	void releaseOnRelease() {
		assertDoesNotThrow(subject::release);
		assertDoesNotThrow(subject::archive);
	}

	@Test
	void gettersWork() {
		// expect:
		assertSame(app, subject.app());
	}
}
