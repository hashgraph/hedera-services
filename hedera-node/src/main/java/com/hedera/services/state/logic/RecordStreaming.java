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
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.stream.NonBlockingHandoff;
import com.hedera.services.stream.RecordStreamObject;
import com.swirlds.common.crypto.RunningHash;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;

@Singleton
public class RecordStreaming implements Runnable {
	private static final Logger log = LogManager.getLogger(RecordStreaming.class);

	private final TransactionContext txnCtx;
	private final NonBlockingHandoff nonBlockingHandoff;
	private final Consumer<RunningHash> runningHashUpdate;
	private final AccountRecordsHistorian recordsHistorian;
	private final Supplier<MerkleNetworkContext> networkCtx;

	private MerkleNetworkContext curNetworkCtx;

	@Inject
	public RecordStreaming(
			final TransactionContext txnCtx,
			final NonBlockingHandoff nonBlockingHandoff,
			final Consumer<RunningHash> runningHashUpdate,
			final AccountRecordsHistorian recordsHistorian,
			final Supplier<MerkleNetworkContext> networkCtx
	) {
		this.txnCtx = txnCtx;
		this.networkCtx = networkCtx;
		this.nonBlockingHandoff = nonBlockingHandoff;
		this.runningHashUpdate = runningHashUpdate;
		this.recordsHistorian = recordsHistorian;
	}

	@Override
	public void run() {
		curNetworkCtx = networkCtx.get();

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
		runningHashUpdate.accept(rso.getRunningHash());
		if (isInNewBlock(rso)) {
			curNetworkCtx.incrementBlockNo();
			curNetworkCtx.setFirstConsTimeOfCurrentBlock(rso.getTimestamp());
			log.info("Beginning block #{} @ {}", curNetworkCtx.getBlockNo(), rso.getTimestamp());
		}
		while (!nonBlockingHandoff.offer(rso)) {
			/* Cannot proceed until we have handed off the record. */
		}
	}

	public boolean isInNewBlock(RecordStreamObject rso) {
		final var firstBlockTime = curNetworkCtx.getFirstConsTimeOfCurrentBlock();
		final var consTime = rso.getTimestamp();
		boolean result;
		if (firstBlockTime == null) {
			result = true;
		} else {
			result = getPeriod(consTime, PERIOD) != getPeriod(firstBlockTime, PERIOD);
		}
		return result;
	}

	private static final long PERIOD = 2_000L;
}