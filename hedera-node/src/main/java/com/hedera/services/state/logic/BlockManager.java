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
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Supplier;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;

@Singleton
public class BlockManager {
	private static final Logger log = LogManager.getLogger(BlockManager.class);

	public static final long BLOCK_PERIOD_MS = 2_000L;

	private final Supplier<MerkleNetworkContext> networkCtx;
	private final Supplier<RecordsRunningHashLeaf> runningHashLeaf;

	@Inject
	public BlockManager(
			final Supplier<MerkleNetworkContext> networkCtx,
			final Supplier<RecordsRunningHashLeaf> runningHashLeaf
	) {
		this.networkCtx = networkCtx;
		this.runningHashLeaf = runningHashLeaf;
	}

	public void updateCurrentBlockHash(final RunningHash runningHash) {
		runningHashLeaf.get().setRunningHash(runningHash);
	}

	public long getManagedBlockNumberAt(final Instant now) {
		final var curNetworkCtx = networkCtx.get();
		final var firstBlockTime = curNetworkCtx.firstConsTimeOfCurrentBlock();
		if (firstBlockTime == null) {
			curNetworkCtx.setFirstConsTimeOfCurrentBlock(now);
			log.debug("Pending block {} is starting @ {}", curNetworkCtx.getBlockNo(), now);
			return curNetworkCtx.getBlockNo();
		} else {
			final var inSamePeriod = getPeriod(now, BLOCK_PERIOD_MS) == getPeriod(firstBlockTime, BLOCK_PERIOD_MS);
			if (inSamePeriod) {
				return curNetworkCtx.getBlockNo();
			} else {
				return updatedBlockNumberAt(now, curNetworkCtx);
			}
		}
	}

	private long updatedBlockNumberAt(final Instant now, final MerkleNetworkContext curNetworkCtx) {
		Hash computedHash;
		try {
			computedHash = runningHashLeaf.get().getLatestBlockHash();
		} catch (final InterruptedException e) {
			final var curBlockNo = curNetworkCtx.getBlockNo();
			// This is almost certainly fatal, hence the ERROR log level
			log.error("Interrupted when computing hash for block #{}", curBlockNo);
			Thread.currentThread().interrupt();
			return curBlockNo;
		}
		log.debug("Finishing block {} (started @ {}), starting new block @ {}",
				curNetworkCtx.getBlockNo(), curNetworkCtx.firstConsTimeOfCurrentBlock(), now);
		return curNetworkCtx.finishBlock(computedHash, now);
	}
}
