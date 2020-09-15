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
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

/**
 * Provides the state transition for token creation.
 *
 * @author Michael Tinker
 */
public class TokenCreateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenCreateTransitionLogic.class);

	private final TokenStore store;
	private final HederaLedger ledger;
	private final TransactionContext txnCtx;

	public TokenCreateTransitionLogic(
			TokenStore store,
			HederaLedger ledger,
			TransactionContext txnCtx
	) {
		this.store = store;
		this.ledger = ledger;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		try {
			transitionFor(txnCtx.accessor().getTxn().getTokenCreation());
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
			abortWith(FAIL_INVALID);
		}
	}

	private void transitionFor(TokenCreation op) {
		var result = store.createProvisionally(op, txnCtx.activePayer(), txnCtx.consensusTime().getEpochSecond());
		if (result.getStatus() != OK) {
			abortWith(result.getStatus());
			return;
		}

		var created = result.getCreated().get();
		var treasury = op.getTreasury();
		var scaledInitialFloat = initialTinyFloat(op.getFloat(), op.getDivisibility());

		var status = OK;
		if (op.hasFreezeKey()) {
			status = ledger.unfreeze(treasury, created);
		}
		if (status == OK && op.hasKycKey()) {
			status = ledger.grantKyc(treasury, created);
		}
		if (status == OK) {
			status = ledger.adjustTokenBalance(treasury, created, scaledInitialFloat);
		}

		if (status != OK) {
			abortWith(status);
			return;
		}

		store.commitCreation();
		txnCtx.setCreated(created);
		txnCtx.setStatus(SUCCESS);
	}

	/* The preconditions on validity of this computation must be enforced by the TokenStore. */
	private long initialTinyFloat(long initialFloat, int divisibility) {
		return BigInteger.valueOf(initialFloat)
				.multiply(BigInteger.valueOf(10).pow(divisibility))
				.longValueExact();
	}

	private void abortWith(ResponseCodeEnum cause) {
		if (store.isCreationPending()) {
			store.rollbackCreation();
		}
		ledger.dropPendingTokenChanges();
		txnCtx.setStatus(cause);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenCreation;
	}
}
