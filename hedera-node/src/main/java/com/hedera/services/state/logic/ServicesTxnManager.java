package com.hedera.services.state.logic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.ServicesContext;
import com.hedera.services.utils.TxnAccessor;

import java.time.Instant;
import java.util.function.BiConsumer;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

public class ServicesTxnManager {
	private final Runnable scopedProcessing;
	private final Runnable scopedRecordStreaming;
	private final Runnable scopedTriggeredProcessing;
	private final BiConsumer<Exception, String> warning;

	public ServicesTxnManager(
			Runnable scopedProcessing,
			Runnable scopedRecordStreaming,
			Runnable scopedTriggeredProcessing,
			BiConsumer<Exception, String> warning
	) {
		this.warning = warning;
		this.scopedProcessing = scopedProcessing;
		this.scopedRecordStreaming = scopedRecordStreaming;
		this.scopedTriggeredProcessing = scopedTriggeredProcessing;
	}

	private boolean createdStreamableRecord;

	public void process(
			TxnAccessor accessor,
			Instant consensusTime,
			long submittingMember,
			ServicesContext ctx
	) {
		createdStreamableRecord = false;

		try {
			ctx.ledger().begin();
			ctx.txnCtx().resetFor(accessor, consensusTime, submittingMember);
			if (accessor.isTriggeredTxn()) {
				scopedTriggeredProcessing.run();
			} else {
				scopedProcessing.run();
			}
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
			TxnAccessor accessor,
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
			TxnAccessor accessor,
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
