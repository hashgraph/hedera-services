package com.hedera.services.txns.network;

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

import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class FreezeTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(FreezeTransitionLogic.class);

	private final FileID softwareUpdateZipFid;
	private final LegacyFreezer delegate;
	private final TransactionContext txnCtx;

	private final Function<TransactionBody, ResponseCodeEnum> SYNTAX_CHECK = this::validate;

	public FreezeTransitionLogic(FileNumbers fileNums, LegacyFreezer delegate, TransactionContext txnCtx) {
		this.txnCtx = txnCtx;
		this.delegate = delegate;

		softwareUpdateZipFid = fileNums.toFid(fileNums.softwareUpdateZip());
	}

	@FunctionalInterface
	public interface LegacyFreezer {
		TransactionRecord perform(TransactionBody txn, Instant consensusTime);
	}

	@Override
	public void doStateTransition() {
		try {
			var freezeTxn = txnCtx.accessor().getTxn();

			var legacyRecord = delegate.perform(freezeTxn, txnCtx.consensusTime());

			txnCtx.setStatus(legacyRecord.getReceipt().getStatus());
		} catch (Exception e) {
			log.warn("Avoidable exception!", e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasFreeze;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> syntaxCheck() {
		return SYNTAX_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody freezeTxn) {
		var op = freezeTxn.getFreeze();
		if (!isValidTime(op.getStartHour(), op.getStartMin()) || !isValidTime(op.getEndHour(), op.getEndMin())) {
			return INVALID_FREEZE_TRANSACTION_BODY;
		}
		if (op.hasUpdateFile()) {
			if (!op.getUpdateFile().equals(softwareUpdateZipFid)) {
				return INVALID_FILE_ID;
			}
			if (op.getFileHash().isEmpty()) {
				return INVALID_FREEZE_TRANSACTION_BODY;
			}
		}
		return OK;
	}

	private boolean isValidTime(int hr, int min) {
		return hr >= 0 && hr <= 23 && min >= 0 && min <= 59;
	}
}
