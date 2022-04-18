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

import com.hedera.services.context.properties.BootstrapProperties;
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

import static com.hedera.services.state.logic.BlockManager.BLOCK_PERIOD_MS;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BlockManagerTest {
	@Mock
	private BootstrapProperties bootstrapProperties;
	@Mock
	private MerkleNetworkContext networkContext;
	@Mock
	private RecordsRunningHashLeaf runningHashLeaf;

	private BlockManager subject;

	@BeforeEach
	void setUp() {
		subject = new BlockManager(bootstrapProperties, () -> networkContext, () -> runningHashLeaf);
	}

	@Test
	void initializesBlockConceptAsExpected() {
		final var lastKnownNo = 1234L;
		final var knownStartTime = Instant.parse("2022-04-01T00:00:00Z");
		final var effCurBlockStart = Instant.parse("2022-04-18T12:27:58Z");
		given(bootstrapProperties.getInstantProperty("bootstrap.lastKnownBlockStartTime"))
				.willReturn(knownStartTime);
		given(bootstrapProperties.getLongProperty("bootstrap.lastKnownBlockNumber"))
				.willReturn(lastKnownNo);

		final var periodOfKnownBlock = getPeriod(knownStartTime, BLOCK_PERIOD_MS);
		final var periodOfCurrentBlock = getPeriod(effCurBlockStart, BLOCK_PERIOD_MS);
		final var blockNoDelta = periodOfCurrentBlock - periodOfKnownBlock;
		final var expectedCurBlockNo = lastKnownNo + blockNoDelta;

		final var newBlockNo = subject.getManagedBlockNumberAt(effCurBlockStart);

		verify(networkContext).setBlockNo(expectedCurBlockNo);
		verify(networkContext).setFirstConsTimeOfCurrentBlock(effCurBlockStart);
		assertEquals(expectedCurBlockNo, newBlockNo);
	}

	@Test
	void delegatesCurrentBlockNumberToContext() {
		given(networkContext.getBlockNo()).willReturn(someBlockNo);

		assertEquals(someBlockNo, subject.getCurrentBlockNumber());
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
