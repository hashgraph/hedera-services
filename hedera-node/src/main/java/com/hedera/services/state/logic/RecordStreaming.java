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
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stream.NonBlockingHandoff;
import com.hedera.services.stream.RecordStreamObject;
import com.swirlds.common.crypto.RunningHash;

import java.time.Instant;
import java.util.function.Consumer;

public class RecordStreaming implements Runnable {
	private final TransactionContext txnCtx;
	private final NonBlockingHandoff nonBlockingHandoff;
	private final Consumer<RunningHash> runningHashUpdate;
	private final AccountRecordsHistorian recordsHistorian;

	public RecordStreaming(
			TransactionContext txnCtx,
			NonBlockingHandoff nonBlockingHandoff,
			Consumer<RunningHash> runningHashUpdate,
			AccountRecordsHistorian recordsHistorian
	) {
		this.txnCtx = txnCtx;
		this.nonBlockingHandoff = nonBlockingHandoff;
		this.runningHashUpdate = runningHashUpdate;
		this.recordsHistorian = recordsHistorian;
	}

	@Override
	public void run() {
		recordsHistorian.lastCreatedRecord().ifPresent(record ->
				stream(txnCtx.accessor().getSignedTxnWrapper(), record, txnCtx.consensusTime()));
	}

	private void stream(
			com.hederahashgraph.api.proto.java.Transaction txn,
			ExpirableTxnRecord expiringRecord,
			Instant consensusTime
	) {
		final var rso = new RecordStreamObject(expiringRecord, txn, consensusTime);
		runningHashUpdate.accept(rso.getRunningHash());
		while (!nonBlockingHandoff.offer(rso)) {
			/* Cannot proceed until we have handed off the record. */
		}
	}
}
