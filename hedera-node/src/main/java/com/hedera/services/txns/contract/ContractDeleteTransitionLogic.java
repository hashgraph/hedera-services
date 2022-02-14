package com.hedera.services.txns.contract;

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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.contract.helpers.DeletionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

@Singleton
public class ContractDeleteTransitionLogic implements TransitionLogic {
	private final DeletionLogic deletionLogic;
	private final TransactionContext txnCtx;

	@Inject
	public ContractDeleteTransitionLogic(final DeletionLogic deletionLogic, final TransactionContext txnCtx) {
		this.txnCtx = txnCtx;
		this.deletionLogic = deletionLogic;
	}

	@Override
	public void doStateTransition() {
		final var contractDeleteTxn = txnCtx.accessor().getTxn();
		final var op = contractDeleteTxn.getContractDeleteInstance();

		final var deleted = deletionLogic.performFor(op);
//			if (op.hasTransferAccountID()) {
//				final var receiver = op.getTransferAccountID();
//				final var result = ledger.lookUpAndValidateAliasedId(receiver, INVALID_TRANSFER_ACCOUNT_ID);
//				if (result.response() != OK) {
//					txnCtx.setStatus(result.response());
//					return;
//				}
//			}

		txnCtx.setTargetedContract(deleted);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractDeleteInstance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	public ResponseCodeEnum validate(final TransactionBody contractDeleteTxn) {
		return deletionLogic.precheckValidity(contractDeleteTxn.getContractDeleteInstance());
	}
}
