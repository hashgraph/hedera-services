package com.hedera.services.state.logic;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.utils.PlatformTxnAccessor;

import java.time.Instant;
import java.util.function.BiConsumer;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

public class ServicesTxnManager {
	private final Runnable scopedProcessing;
	private final Runnable scopedRecordStreaming;
	private final BiConsumer<Exception, String> warning;

	public ServicesTxnManager(
			Runnable scopedProcessing,
			Runnable scopedRecordStreaming,
			BiConsumer<Exception, String> warning
	) {
		this.warning = warning;
		this.scopedProcessing = scopedProcessing;
		this.scopedRecordStreaming = scopedRecordStreaming;
	}

	private boolean createdStreamableRecord;

	public void process(
			PlatformTxnAccessor accessor,
			Instant consensusTime,
			long submittingMember,
			ServicesContext ctx
	) {
		createdStreamableRecord = false;

		try {
			ctx.ledger().begin();
			ctx.txnCtx().resetFor(accessor, consensusTime, submittingMember);
			scopedProcessing.run();
		} catch (Exception processFailure) {
			warning.accept(processFailure, "txn processing");
			ctx.txnCtx().setStatus(FAIL_INVALID);
		} finally {
			attemptCommit(accessor, consensusTime, submittingMember, ctx);
			if (createdStreamableRecord) {
				attemptRecordStreaming();
			}
		}
	}

	private void attemptRecordStreaming() {
		try {
			scopedRecordStreaming.run();
		} catch (Exception streamingFailure) {
			warning.accept(streamingFailure, "record streaming");
		}
	}

	private void attemptCommit(
			PlatformTxnAccessor accessor,
			Instant consensusTime,
			long submittingMember,
			ServicesContext ctx
	) {
		try {
			ctx.ledger().commit();
			createdStreamableRecord = true;
		} catch (Exception commitFailure) {
			warning.accept(commitFailure, "txn commit");
			attemptRollback(accessor, consensusTime, submittingMember, ctx);
		}
	}

	private void attemptRollback(
			PlatformTxnAccessor accessor,
			Instant consensusTime,
			long submittingMember,
			ServicesContext ctx
	) {
		try {
			ctx.recordCache().setFailInvalid(
					ctx.txnCtx().effectivePayer(),
					accessor,
					consensusTime,
					submittingMember);
		} catch (Exception recordFailure) {
			warning.accept(recordFailure, "creating failure record");
		}
		try {
			ctx.ledger().rollback();
		} catch (Exception rollbackFailure) {
			warning.accept(rollbackFailure, "txn rollback");
		}
	}
}
