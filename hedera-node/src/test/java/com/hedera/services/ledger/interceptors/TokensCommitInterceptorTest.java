package com.hedera.services.ledger.interceptors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.CommitInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doCallRealMethod;

class TokensCommitInterceptorTest {
	@Test
	void everythingNoopForNow() {
		final var subject = new TokensCommitInterceptor(new SideEffectsTracker());

		assertDoesNotThrow(() -> subject.preview(null));
	}

	@Test
	void defaultFinisherIsNull() {
		final var subject = Mockito.mock(CommitInterceptor.class);
		doCallRealMethod().when(subject).finisherFor(0);
		assertNull(subject.finisherFor(0));
	}
}
