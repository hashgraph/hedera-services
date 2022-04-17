package com.hedera.services.state.logic;

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

import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.test.utils.TxnUtils;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BlockManagerTest {
	@Mock
	private MerkleNetworkContext networkContext;
	@Mock
	private RecordsRunningHashLeaf runningHashLeaf;

	private BlockManager subject;

	@BeforeEach
	void setUp() {
		subject = new BlockManager(() -> networkContext, () -> runningHashLeaf);
	}

	@Test
	void usesCurrentBlockNumberAndUpdatesFirstConsTimeIfCurrentlyNull() {
		given(networkContext.getBlockNo()).willReturn(someBlockNo);

		final var newBlockNo = subject.getManagedBlockNumberAt(aTime);

		verify(networkContext).setFirstConsTimeOfCurrentBlock(aTime);
		assertEquals(someBlockNo, newBlockNo);
	}

	@Test
	void continuesWithCurrentBlockIfInSamePeriod() {
		given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
		given(networkContext.getBlockNo()).willReturn(someBlockNo);

		final var newBlockNo = subject.getManagedBlockNumberAt(someTime);

		assertEquals(someBlockNo, newBlockNo);
		verify(networkContext, never()).setFirstConsTimeOfCurrentBlock(any());
		verify(networkContext, never()).finishBlock(any(), any());
	}

	@Test
	void finishesBlockIfNotInSamePeriod() throws InterruptedException {
		given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
		given(networkContext.finishBlock(aFullBlockHash, anotherTime)).willReturn(someBlockNo);
		given(runningHashLeaf.getLatestBlockHash()).willReturn(aFullBlockHash);

		final var newBlockNo = subject.getManagedBlockNumberAt(anotherTime);

		assertEquals(someBlockNo, newBlockNo);
	}

	@Test
	void returnsCurrentBlockNoIfSomehowInterrupted() throws InterruptedException {
		given(networkContext.firstConsTimeOfCurrentBlock()).willReturn(aTime);
		given(runningHashLeaf.getLatestBlockHash()).willThrow(InterruptedException.class);
		given(networkContext.getBlockNo()).willReturn(someBlockNo);

		final var newBlockNo = subject.getManagedBlockNumberAt(anotherTime);

		assertEquals(someBlockNo, newBlockNo);
	}

	@Test
	void updatesRunningHashAsExpected() {
		final var someHash = new RunningHash();
		subject.updateCurrentBlockHash(someHash);
		verify(runningHashLeaf).setRunningHash(someHash);
	}

	private static final long someBlockNo = 123_456;
	private static final Instant aTime = Instant.ofEpochSecond(1_234_567L, 890);
	private static final Instant someTime = Instant.ofEpochSecond(1_234_567L, 890_000);
	private static final Instant anotherTime = Instant.ofEpochSecond(1_234_568L, 890);
	private static final Hash aFullBlockHash = new Hash(TxnUtils.randomUtf8Bytes(48));
}
