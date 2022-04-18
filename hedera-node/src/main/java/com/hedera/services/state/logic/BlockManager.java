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
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Supplier;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;

/**
 * Manages the block-related fields in the {@link MerkleNetworkContext}, based on 2-second "periods" in
 * consensus time. Whenever a user transaction's consensus timestamp is the first in a new period,
 * starts a new block by recording the running hash of the last transaction in the current period and
 * incrementing the block number via a call to {@link MerkleNetworkContext#finishBlock(Hash, Instant)}.
 */
@Singleton
public class BlockManager {
	private static final Logger log = LogManager.getLogger(BlockManager.class);

	public static final long BLOCK_PERIOD_MS = 2_000L;

	private final BootstrapProperties bootstrapProperties;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final Supplier<RecordsRunningHashLeaf> runningHashLeaf;

	@Inject
	public BlockManager(
			final BootstrapProperties bootstrapProperties,
			final Supplier<MerkleNetworkContext> networkCtx,
			final Supplier<RecordsRunningHashLeaf> runningHashLeaf
	) {
		this.networkCtx = networkCtx;
		this.runningHashLeaf = runningHashLeaf;
		this.bootstrapProperties = bootstrapProperties;
	}

	/**
	 * Accepts the {@link RunningHash} that, <b>if</b> the corresponding user transaction is the last in this
	 * 2-second period, will be the "block hash" for the current block.
	 *
	 * @param runningHash the latest candidate for the current block hash
	 */
	public void updateCurrentBlockHash(final RunningHash runningHash) {
		runningHashLeaf.get().setRunningHash(runningHash);
	}

	/**
	 * Provides the current block number.
	 *
	 * @return the current block number
	 */
	public long getCurrentBlockNumber() {
		return networkCtx.get().getBlockNo();
	}

	/**
	 * Given the consensus timestamp of a user transaction, manages the block-related fields in the
	 * {@link MerkleNetworkContext}, finishing the current block if appropriate.
	 *
	 * @param now the latest consensus timestamp of a user transaction
	 * @return the new block number, taking this timestamp into account
	 */
	public long getManagedBlockNumberAt(final Instant now) {
		final var curNetworkCtx = networkCtx.get();
		final var firstBlockTime = curNetworkCtx.firstConsTimeOfCurrentBlock();
		// Only possible when handling the first transaction after the 0.26 upgrade; from then
		// on there will always be a current block with a first consensus time
		if (firstBlockTime == null) {
			final var lastKnownBlockNo =
					bootstrapProperties.getLongProperty("bootstrap.lastKnownBlockNumber");
			final var lastKnownBlockStartTime =
					bootstrapProperties.getInstantProperty("bootstrap.lastKnownBlockStartTime");
			final var elapsedPeriodsSinceLastKnownBlock =
					getPeriod(now, BLOCK_PERIOD_MS) - getPeriod(lastKnownBlockStartTime, BLOCK_PERIOD_MS);
			final var currentBlockNo = lastKnownBlockNo + elapsedPeriodsSinceLastKnownBlock;
			curNetworkCtx.setBlockNo(currentBlockNo);
			curNetworkCtx.setFirstConsTimeOfCurrentBlock(now);
			log.info("First conceptualized block {} is starting @ {}", currentBlockNo, now);
			return currentBlockNo;
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
