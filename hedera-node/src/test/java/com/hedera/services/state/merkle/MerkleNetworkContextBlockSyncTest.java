package com.hedera.services.state.merkle;

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

import com.hedera.services.sysfiles.domain.KnownBlockValues;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.SplittableRandom;

import static com.hedera.services.state.merkle.MerkleNetworkContext.UNAVAILABLE_BLOCK_HASH;
import static org.junit.jupiter.api.Assertions.*;

class MerkleNetworkContextBlockSyncTest {
	private static final int m = 64;
	private static final byte[] unmatchedHash = new byte[32];
	private static final byte[][] blockHashes = new byte[m][];
	private static final byte[][] swirldHashes = new byte[m][];
	private static final long knownBlockNo = 666;

	static {
		final var r = new SplittableRandom(123456789);
		for (int i = 0; i < m; i++) {
			swirldHashes[i] = new byte[48];
			blockHashes[i] = new byte[32];
			r.nextBytes(swirldHashes[i]);
			System.arraycopy(swirldHashes[i], 16, blockHashes[i], 0, 32);
		}
		r.nextBytes(unmatchedHash);
	}

	private static final Instant then = Instant.ofEpochSecond(1_234_567, 890);

	private final MerkleNetworkContext subject = new MerkleNetworkContext();

	@Test
	void finishingWithoutKnownBlockNumberUsesNegativeBlockNumbers() {
		final var miniN = m / 16;
		finishNBlocks(miniN);
		for (int i = 0; i < miniN; i++) {
			assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHashByNumber(Long.MIN_VALUE + i));
		}
	}

	@Test
	void unmatchedHashIsNoop() {
		subject.renumberBlocksToMatch(new KnownBlockValues(unmatchedHash, knownBlockNo));
		assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHashByNumber(knownBlockNo));
	}

	@Test
	void renumbersAsExpected() {
		finishNBlocks(m);
		final var blockOffset = m / 4;
		subject.renumberBlocksToMatch(new KnownBlockValues(blockHashes[blockOffset], knownBlockNo));
		assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHashByNumber(knownBlockNo - blockOffset - 1));
		for (int i = 0; i < m; i++) {
			assertArrayEquals(
					blockHashes[i],
					subject.getBlockHashByNumber(knownBlockNo - blockOffset + i).toArray());
		}
		assertSame(UNAVAILABLE_BLOCK_HASH, subject.getBlockHashByNumber(knownBlockNo + m - blockOffset + 1));
	}

	private void finishNBlocks(final int n) {
		for (int i = 0; i < n; i++) {
			subject.finishBlock(new Hash(swirldHashes[i]), then.plusNanos(i));
		}
	}
}
