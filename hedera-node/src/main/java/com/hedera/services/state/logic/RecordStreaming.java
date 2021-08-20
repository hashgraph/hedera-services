package com.hedera.services.state.logic;

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
