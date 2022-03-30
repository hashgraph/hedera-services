package com.hedera.services.txns.crypto.validators;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AllowanceChecksTest {
	AllowanceChecks subject;

	@BeforeEach
	public void setUp() {
		subject = mock(AllowanceChecks.class);
	}

	@Test
	void unsupportedMethods() {
		willCallRealMethod().given(subject).validateTokenAmount(any(), anyLong(), any(), any());
		willCallRealMethod().given(subject).validateSerialNums(anyList(), any(), any(), any(), any());
		willCallRealMethod().given(subject).validateAmount(anyLong(), any(), any());
		assertThrows(UnsupportedOperationException.class,
				() -> subject.validateSerialNums(anyList(), any(), any(), any(), any()));
		assertThrows(UnsupportedOperationException.class,
				() -> subject.validateTokenAmount(any(), anyLong(), any(), any()));
		assertThrows(UnsupportedOperationException.class,
				() -> subject.validateAmount(anyLong(), any(), any()));
	}
}
