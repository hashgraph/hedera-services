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
import com.hedera.services.ledger.accounts.AccountCustomizer;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.tokens.TokenScope;
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenManagement;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;
import java.util.function.Predicate;

import static com.hedera.services.tokens.TokenStore.MISSING_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_REF;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

/**
 * Provides the state transition for token updates.
 *
 * @author Michael Tinker
 */
public class TokenUpdateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenUpdateTransitionLogic.class);

	private final TokenStore store;
	private final HederaLedger ledger;
	private final TransactionContext txnCtx;

	public TokenUpdateTransitionLogic(
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
			transitionFor(txnCtx.accessor().getTxn().getTokenUpdate());
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxn4Log(), e);
			abortWith(FAIL_INVALID);
		}
	}

	private void transitionFor(TokenManagement op) {
		var id = store.resolve(op.getToken());
		if (id == MISSING_TOKEN) {
			txnCtx.setStatus(INVALID_TOKEN_REF);
			return;
		}

		var outcome = OK;
		MerkleToken token = store.get(id);
		Optional<AccountID> replacedTreasury = Optional.empty();
		if (op.hasTreasury()) {
			outcome = prepNewTreasury(id, token, op.getTreasury());
			if (outcome != OK) {
				txnCtx.setStatus(outcome);
				return;
			}
			replacedTreasury = Optional.of(token.treasury().toGrpcAccountId());
		}

		outcome = store.update(op);
		if (outcome != OK) {
			abortWith(outcome);
			return;
		}

		if (replacedTreasury.isPresent()) {
			outcome = store.wipe(replacedTreasury.get(), id, true);
		}

		txnCtx.setStatus((outcome == OK) ? SUCCESS : outcome);
	}

	private ResponseCodeEnum prepNewTreasury(TokenID id, MerkleToken token, AccountID treasury) {
		var status = OK;
		if (token.hasFreezeKey()) {
			status = ledger.unfreeze(treasury, id);
		}
		if (status == OK && token.hasKycKey()) {
			status = ledger.grantKyc(treasury, id);
		}
		return status;
	}

	private void abortWith(ResponseCodeEnum cause) {
		ledger.dropPendingTokenChanges();
		txnCtx.setStatus(cause);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenUpdate;
	}
}
