package com.hedera.services.txns.network;

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

import com.hedera.services.legacy.handler.FreezeHandler;
import com.swirlds.common.NodeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UpdateHelperTest {
	private final NodeId zeroId = new NodeId(false, 0);
	private final NodeId nonZeroId = new NodeId(false, 1);

	@Mock
	private FreezeHandler delegate;

	private UpdateHelper subject;

	@Test
	void alwaysDelegatesIfNotOnMac() {
		// setup:
		subject = new UpdateHelper(nonZeroId, delegate);

		// when:
		subject.runIfAppropriateOn("ubuntu");

		// then:
		verify(delegate).handleUpdateFeature();
	}

	@Test
	void zeroDelegatesIfOnMac() {
		// setup:
		subject = new UpdateHelper(zeroId, delegate);

		// when:
		subject.runIfAppropriateOn("mac");

		// then:
		verify(delegate).handleUpdateFeature();
	}

	@Test
	void nonZeroDelegatesIfOnMac() {
		// setup:
		subject = new UpdateHelper(nonZeroId, delegate);

		// when:
		subject.runIfAppropriateOn("mac");

		// then:
		verify(delegate, never()).handleUpdateFeature();
	}
}
