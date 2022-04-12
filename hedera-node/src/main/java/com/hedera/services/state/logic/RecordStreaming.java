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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.stream.NonBlockingHandoff;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;

@Singleton
public class RecordStreaming implements Runnable {
	private static final Logger log = LogManager.getLogger(RecordStreaming.class);

	private final TransactionContext txnCtx;
	private final NonBlockingHandoff nonBlockingHandoff;
	private final Consumer<RunningHash> runningHashUpdate;
	private final RecordsHistorian recordsHistorian;
	private final Supplier<RecordsRunningHashLeaf> runningHashLeaf;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private MerkleNetworkContext curNetworkCtx;
	private RecordsRunningHashLeaf curRunningHashLeaf;

	@Inject
	public RecordStreaming(
			final TransactionContext txnCtx,
			final NonBlockingHandoff nonBlockingHandoff,
			final Consumer<RunningHash> runningHashUpdate,
			final RecordsHistorian recordsHistorian,
			final Supplier<MerkleNetworkContext> networkCtx,
			final Supplier<RecordsRunningHashLeaf> runningHashLeaf
	) {
		this.txnCtx = txnCtx;
		this.networkCtx = networkCtx;
		this.nonBlockingHandoff = nonBlockingHandoff;
		this.runningHashUpdate = runningHashUpdate;
		this.runningHashLeaf = runningHashLeaf;
		this.recordsHistorian = recordsHistorian;
	}

	@Override
	public void run() {
		curNetworkCtx = networkCtx.get();
		curRunningHashLeaf = runningHashLeaf.get();

		if (recordsHistorian.hasPrecedingChildRecords()) {
			for (final var childRso : recordsHistorian.getPrecedingChildRecords()) {
				stream(childRso);
			}
		}

		final var topLevelRecord = recordsHistorian.lastCreatedTopLevelRecord();
		if (topLevelRecord != null) {
			final var topLevelRso = new RecordStreamObject(
					topLevelRecord,
					txnCtx.accessor().getSignedTxnWrapper(),
					txnCtx.consensusTime());
			stream(topLevelRso);
		}

		if (recordsHistorian.hasFollowingChildRecords()) {
			for (final var childRso : recordsHistorian.getFollowingChildRecords()) {
				stream(childRso);
			}
		}
	}

	public void stream(final RecordStreamObject rso) {
		if (isInNewBlock(rso)) {
			Hash computedHash;
			try {
				computedHash = curRunningHashLeaf.getLatestBlockHash();
			} catch (final InterruptedException e) {
				log.error("Error in computing hash for block #{}", curNetworkCtx.getBlockNo());
				Thread.currentThread().interrupt();
				return;
			}

			curNetworkCtx.finishCurrentBlock(computedHash);
			curNetworkCtx.startNewBlock(rso.getTimestamp());
			log.info("Beginning block #{} @ {}", curNetworkCtx.getBlockNo(), rso.getTimestamp());
		}

		runningHashUpdate.accept(rso.getRunningHash());
		curNetworkCtx.setLatestConsTimeOfCurrentBlock(rso.getTimestamp());
		while (!nonBlockingHandoff.offer(rso)) {
			/* Cannot proceed until we have handed off the record. */
		}
	}

	public boolean isInNewBlock(final RecordStreamObject rso) {
		final var firstBlockTime = curNetworkCtx.getFirstConsTimeOfCurrentBlock();
		final var latestBlockTime = curNetworkCtx.getLatestConsTimeOfCurrentBlock();
		final var consTime = rso.getTimestamp();
		boolean result;
		if (firstBlockTime == null) {
			result = true;
		} else {
			final Duration duration = Duration.between(latestBlockTime, consTime);
			result = getPeriod(consTime, BLOCK_PERIOD_MS) != getPeriod(firstBlockTime, BLOCK_PERIOD_MS) && duration.toNanos() >= MIN_TRANS_TIMESTAMP_INCR_NANOS;
			if(result) {
				log.info("consTime is {}", consTime);
				log.info("firstBlockTime is {}", firstBlockTime);
			}
		}
		return result;
	}

	private static final long BLOCK_PERIOD_MS = 2_000L;
	private static final long MIN_TRANS_TIMESTAMP_INCR_NANOS = 1_000L;
}