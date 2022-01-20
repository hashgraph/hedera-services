package com.hedera.services.state.logic;

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

import com.hedera.services.utils.TxnAccessor;
import com.hedera.test.extensions.LoggingSubject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;

@ExtendWith({ MockitoExtension.class })
class ThrottleScreenTest {

	@Mock
	NetworkCtxManager networkCtxManager;
	@Mock
	private TxnAccessor accessor;
	@LoggingSubject
	private ThrottleScreen subject;

	@BeforeEach
	void setUp() {
		subject = new ThrottleScreen(networkCtxManager);
	}

	@Test
	void propagatesOKStatus() {
		given(networkCtxManager.prepareForIncorporating(accessor)).willReturn(OK);

		// when:
		final var result = subject.applyTo(accessor);

		// then:
		Assertions.assertEquals(OK, result);
	}

	@Test
	void propagatesBUSYStatus() {
		given(networkCtxManager.prepareForIncorporating(accessor)).willReturn(BUSY);

		// when:
		final var result = subject.applyTo(accessor);

		// then:
		Assertions.assertEquals(BUSY, result);
	}
}
