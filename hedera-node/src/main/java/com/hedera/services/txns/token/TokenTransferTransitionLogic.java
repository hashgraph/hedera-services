package com.hedera.services.txns.token;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;


public class TokenTransferTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenTransferTransitionLogic.class);

	private final HederaLedger ledger;
	private final TransactionContext txnCtx;

	public TokenTransferTransitionLogic(HederaLedger ledger, TransactionContext txnCtx) {
		this.ledger = ledger;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		try {
			var op = txnCtx.accessor().getTxn().getTokenTransfers();
			var outcome = ledger.doAtomicZeroSumTokenTransfers(op);
			txnCtx.setStatus(outcome == OK ? SUCCESS : outcome);
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
			txnCtx.setStatus(FAIL_INVALID);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenTransfers;
	}
}
