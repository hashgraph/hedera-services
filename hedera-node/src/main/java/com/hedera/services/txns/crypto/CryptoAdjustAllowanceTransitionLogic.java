package com.hedera.services.txns.crypto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.crypto.validators.AdjustAllowanceChecks;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class CryptoAdjustAllowanceTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;
	private final AdjustAllowanceChecks adjustAllowanceChecks;
	private final GlobalDynamicProperties dynamicProperties;
	private final AdjustAllowanceLogic adjustAllowanceLogic;

	@Inject
	public CryptoAdjustAllowanceTransitionLogic(
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final AdjustAllowanceChecks allowanceChecks,
			final GlobalDynamicProperties dynamicProperties,
			final AdjustAllowanceLogic adjustAllowanceLogic) {
		this.txnCtx = txnCtx;
		this.accountStore = accountStore;
		this.adjustAllowanceChecks = allowanceChecks;
		this.dynamicProperties = dynamicProperties;
		this.adjustAllowanceLogic = adjustAllowanceLogic;
	}

	@Override
	public void doStateTransition() {
		/* --- Extract gRPC --- */
		final TransactionBody cryptoAdjustAllowanceTxn = txnCtx.accessor().getTxn();
		final AccountID payer = cryptoAdjustAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		adjustAllowanceLogic.adjustAllowance(op.getCryptoAllowancesList(),
											 op.getTokenAllowancesList(),
											 op.getNftAllowancesList(),
											 payer);

		txnCtx.setStatus(SUCCESS);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoAdjustAllowance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	private ResponseCodeEnum validate(TransactionBody cryptoAllowanceTxn) {
		final AccountID payer = cryptoAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoAllowanceTxn.getCryptoAdjustAllowance();
		final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(payer));
		return adjustAllowanceChecks.allowancesValidation(op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(), op.getNftAllowancesList(), payerAccount,
				dynamicProperties.maxAllowanceLimitPerTransaction());
	}

}
