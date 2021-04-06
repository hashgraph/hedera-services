package com.hedera.services.throttling;

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

import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HapiThrottlingTest {
	@Mock
	TimedFunctionalityThrottling delegate;

	HapiThrottling subject;

	@BeforeEach
	void setUp() {
		subject = new HapiThrottling(delegate);
	}

	@Test
	void delegatesWithSomeInstant() {
		given(delegate.shouldThrottle(any(), any())).willReturn(true);

		// when:
		var ans = subject.shouldThrottle(CryptoTransfer);

		// then:
		assertTrue(ans);
		verify(delegate).shouldThrottle(eq(CryptoTransfer), any());
	}

	@Test
	void unsupportedMethodsThrow() {
		// expect:
		assertThrows(UnsupportedOperationException.class, () -> subject.activeThrottlesFor(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.allActiveThrottles());
	}

	@Test
	void delegatesRebuild() {
		// setup:
		ThrottleDefinitions defs = new ThrottleDefinitions();

		// when:
		subject.rebuildFor(defs);

		// then:
		verify(delegate).rebuildFor(defs);
	}
}
