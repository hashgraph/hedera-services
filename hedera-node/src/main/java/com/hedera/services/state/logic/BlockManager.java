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
import com.hedera.services.contracts.execution.HederaBlockValues;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.utility.Units;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleNetworkContext.UNAVAILABLE_BLOCK_HASH;
import static com.hedera.services.state.merkle.MerkleNetworkContext.ethHashFrom;
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

	private final long blockPeriodMs;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final Supplier<RecordsRunningHashLeaf> runningHashLeaf;
	private long provisionalBlockNumber = Long.MIN_VALUE;
	private org.hyperledger.besu.datatypes.Hash provisionalBlockHash = UNAVAILABLE_BLOCK_HASH;

	@Inject
	public BlockManager(
			final BootstrapProperties bootstrapProperties,
			final Supplier<MerkleNetworkContext> networkCtx,
			final Supplier<RecordsRunningHashLeaf> runningHashLeaf
	) {
		this.networkCtx = networkCtx;
		this.runningHashLeaf = runningHashLeaf;

		blockPeriodMs = bootstrapProperties.getLongProperty("hedera.recordStream.logPeriod")
				* Units.SECONDS_TO_MILLISECONDS;
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
		return networkCtx.get().getManagedBlockNo();
	}

	public long getProvisionalBlockNumber() {
		return provisionalBlockNumber;
	}

	public org.hyperledger.besu.datatypes.Hash getProvisionalBlockHash(long blockNumber) {
		if (blockNumber == provisionalBlockNumber) {
			return provisionalBlockHash;
		}
		return networkCtx.get().getBlockHashByNumber(blockNumber);
	}

	/**
	 *
	 * @param timestamp
	 * @param gasLimit
	 * @return
	 */
	public HederaBlockValues createProvisionalBlockValues(@NotNull final Instant timestamp, long gasLimit) {
		var curNetworkCtx = networkCtx.get();

		var blockNo = curNetworkCtx.getBlockNo();

		// Before the 0.26 upgrade, we used the consensus timestamp as the block number; and if we
		// get a zero block number, it means the post-0.26 block sync hasn't happened yet
		Instant provisionalBlockTimestamp;
		if (blockNo == 0) {
			provisionalBlockNumber = timestamp.getEpochSecond();
			provisionalBlockTimestamp = Instant.EPOCH;
			provisionalBlockHash = UNAVAILABLE_BLOCK_HASH;
		} else if (willCreateNewBlock(timestamp)) {
			provisionalBlockNumber = curNetworkCtx.getBlockNo() + 1;
			provisionalBlockTimestamp = timestamp;
			try {
				provisionalBlockHash = ethHashFrom(runningHashLeaf.get().getLatestBlockHash());
			} catch (InterruptedException e) {
				final var curBlockNo = curNetworkCtx.getManagedBlockNo();
				// This is almost certainly fatal, hence the ERROR log level
				log.error("Interrupted when computing hash for block #{}", curBlockNo);
				Thread.currentThread().interrupt();
			}
		} else {
			provisionalBlockNumber = curNetworkCtx.getBlockNo();
			provisionalBlockTimestamp = curNetworkCtx.firstConsTimeOfCurrentBlock();
		}

		return new HederaBlockValues(gasLimit, provisionalBlockNumber, provisionalBlockTimestamp);
	}

	public boolean willCreateNewBlock(@NotNull final Instant timestamp) {
		final var curNetworkCtx = networkCtx.get();
		final var firstBlockTime = curNetworkCtx.firstConsTimeOfCurrentBlock();
		return firstBlockTime == null || !inSamePeriod(firstBlockTime, timestamp);
	}

	/**
	 * Given the consensus timestamp of a user transaction, manages the block-related fields in the
	 * {@link MerkleNetworkContext}, finishing the current block if appropriate.
	 *
	 * @param now the latest consensus timestamp of a user transaction
	 * @return the new block number, taking this timestamp into account
	 */
	public long getManagedBlockNumberAt(@NotNull final Instant now) {
		final var curNetworkCtx = networkCtx.get();
		final var firstBlockTime = curNetworkCtx.firstConsTimeOfCurrentBlock();
		if (firstBlockTime != null && inSamePeriod(firstBlockTime, now)) {
			return curNetworkCtx.getManagedBlockNo();
		} else {
			return updatedBlockNumberAt(now, curNetworkCtx);
		}
	}

	private boolean inSamePeriod(@NotNull final Instant then, @NotNull final Instant now) {
		return getPeriod(now, blockPeriodMs) == getPeriod(then, blockPeriodMs);
	}

	private long updatedBlockNumberAt(final Instant now, final MerkleNetworkContext curNetworkCtx) {
		Hash computedHash;
		try {
			computedHash = runningHashLeaf.get().getLatestBlockHash();
		} catch (final InterruptedException e) {
			final var curBlockNo = curNetworkCtx.getManagedBlockNo();
			// This is almost certainly fatal, hence the ERROR log level
			log.error("Interrupted when computing hash for block #{}", curBlockNo);
			Thread.currentThread().interrupt();
			return curBlockNo;
		}
		log.debug("Finishing block {} (started @ {}), starting new block @ {}",
				curNetworkCtx.getManagedBlockNo(), curNetworkCtx.firstConsTimeOfCurrentBlock(), now);
		return curNetworkCtx.finishBlock(computedHash, now);
	}
}
