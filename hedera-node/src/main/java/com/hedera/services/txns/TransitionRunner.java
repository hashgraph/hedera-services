package com.hedera.services.txns;

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
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;

@Singleton
public class TransitionRunner {
	private static final Logger log = LogManager.getLogger(TransitionRunner.class);

	private static final EnumSet<HederaFunctionality> refactoredOps = EnumSet.of(
			TokenMint, TokenBurn,
			TokenFreezeAccount, TokenUnfreezeAccount,
			TokenGrantKycToAccount, TokenRevokeKycFromAccount,
			TokenAssociateToAccount, TokenDissociateFromAccount,
			TokenAccountWipe,
			TokenCreate,
			TokenFeeScheduleUpdate,
			CryptoTransfer,
			TokenDelete,
			ContractCall
	);

	private final TransactionContext txnCtx;
	private final TransitionLogicLookup lookup;

	@Inject
	public TransitionRunner(TransactionContext txnCtx, TransitionLogicLookup lookup) {
		this.txnCtx = txnCtx;
		this.lookup = lookup;
	}

	/**
	 * Tries to find and run transition logic for the transaction wrapped by the
	 * given accessor.
	 *
	 * @param accessor
	 * 		the transaction accessor
	 * @return true if the logic was run to completion
	 */
	public boolean tryTransition(@Nonnull TxnAccessor accessor) {
		final var txn = accessor.getTxn();
		final var function = accessor.getFunction();
		final var logic = lookup.lookupFor(function, txn);
		if (logic.isEmpty()) {
			log.warn("Transaction w/o applicable transition logic at consensus :: {}", accessor::getSignedTxnWrapper);
			txnCtx.setStatus(FAIL_INVALID);
			return false;
		} else {
			final var transition = logic.get();
			final var validity = transition.validateSemantics(accessor);
			if (validity != OK) {
				txnCtx.setStatus(validity);
				return false;
			}
			try {
				transition.doStateTransition();
				/* Only certain functions are refactored */
				if (refactoredOps.contains(function)) {
					txnCtx.setStatus(SUCCESS);
				}
				transition.resetCreatedIds();
			} catch (InvalidTransactionException ite) {
				final var code = ite.getResponseCode();
				txnCtx.setStatus(code);
				if (code == FAIL_INVALID) {
					log.warn("Avoidable failure in transition logic for {}", accessor.getSignedTxnWrapper(), ite);
				}
				logic.get().reclaimCreatedIds();
			}
			return true;
		}
	}
}
