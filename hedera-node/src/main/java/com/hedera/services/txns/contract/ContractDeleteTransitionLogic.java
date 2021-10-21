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
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OBTAINER_SAME_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class ContractDeleteTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(ContractDeleteTransitionLogic.class);

	private final HederaLedger ledger;
	private final AccountStore accountStore;
	private final TransactionContext txnCtx;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	@Inject
	public ContractDeleteTransitionLogic(
			HederaLedger ledger,
			AccountStore accountStore,
			TransactionContext txnCtx
	) {
		this.ledger = ledger;
		this.accountStore = accountStore;
		this.txnCtx = txnCtx;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var contractDeleteTxn = txnCtx.accessor().getTxn();
		final var op = contractDeleteTxn.getContractDeleteInstance();

		/* --- Load the model objects --- */
		final var target = accountStore.loadContract(Id.fromGrpcContract(op.getContractID()));
		final var beneficiaryId = computeBeneficiary(op);

		/* --- Do the business logic --- */
		if (target.getBalance() > 0L) {
			validateFalse(beneficiaryId.equals(Id.DEFAULT), OBTAINER_REQUIRED);
			validateFalse(beneficiaryId.equals(target.getId()), OBTAINER_SAME_CONTRACT_ID);

			Account beneficiary = accountStore.loadEntityOrFailWith(beneficiaryId, OBTAINER_DOES_NOT_EXIST, null,
					null);
			ledger.doTransfer(target.getId().asGrpcAccount(), beneficiary.getId().asGrpcAccount(), target.getBalance());
		}
		target.setDeleted(true);

		/* --- Persist the changes --- */
		accountStore.persistAccount(target);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractDeleteInstance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		final ContractDeleteTransactionBody op = txnBody.getContractDeleteInstance();
		return !op.hasContractID() ? INVALID_CONTRACT_ID : OK;
	}

	private Id computeBeneficiary(ContractDeleteTransactionBody op) {
		if (op.hasTransferAccountID()) {
			return Id.fromGrpcAccount(op.getTransferAccountID());
		} else if (op.hasTransferContractID()) {
			return Id.fromGrpcContract(op.getTransferContractID());
		}
		return Id.DEFAULT;
	}
}
