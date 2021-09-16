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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.contract.process.CallEvmTxProcessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ContractCallTransitionLogic implements TransitionLogic {

	private final AccountStore accountStore;
	private final HbarCentExchange exchange;
	private final TransactionContext txnCtx;
	private final HederaWorldState worldState;
	private final UsagePricesProvider usagePrices;
	private final GlobalDynamicProperties properties;
	private final TransactionRecordService recordService;

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validateSemantics;

	@Inject
	public ContractCallTransitionLogic(
			TransactionContext txnCtx,
			AccountStore accountStore,
			HbarCentExchange exchange,
			HederaWorldState worldState,
			UsagePricesProvider usagePrices,
			GlobalDynamicProperties properties,
			TransactionRecordService recordService
	) {
		this.txnCtx = txnCtx;
		this.exchange = exchange;
		this.worldState = worldState;
		this.properties = properties;
		this.usagePrices = usagePrices;
		this.accountStore = accountStore;
		this.recordService = recordService;
	}

	@Override
	public void doStateTransition() {

		/* --- Translate from gRPC types --- */
		var contractCallTxn = txnCtx.accessor().getTxn();
		var op = contractCallTxn.getContractCall();
		final var senderId = Id.fromGrpcAccount(contractCallTxn.getTransactionID().getAccountID());
		final var contractId = Id.fromGrpcContract(op.getContractID());

		/* --- Load the model objects --- */
		final var sender = accountStore.loadAccount(senderId);
		final var receiver = accountStore.loadContract(contractId);

		/* --- Do the business logic --- */
		final var txProcessor = new CallEvmTxProcessor(exchange, worldState.updater(), usagePrices, properties);
		final var result = txProcessor.execute(
				sender,
				receiver,
				op.getGas(),
				op.getAmount(),
				op.getFunctionParameters(),
				txnCtx.consensusTime());
		/* In case the EVM runs into RE */
		validateFalse(result.isInvalid(), FAIL_INVALID, result.getValidationResult().getErrorMessage());

		/* --- Persist changes into state --- */
		worldState.persist();
		/* --- Externalise result --- */
		recordService.externaliseCallEvmTransaction(receiver.getId(), result);

	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasContractCall;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	private ResponseCodeEnum validateSemantics(final TransactionBody transactionBody) {
		var op = transactionBody.getContractCall();

		if (op.getGas() > properties.maxGas()) {
			return MAX_GAS_LIMIT_EXCEEDED;
		}
		if (op.getGas() < 0) {
			return CONTRACT_NEGATIVE_GAS;
		}
		if (op.getAmount() < 0) {
			return CONTRACT_NEGATIVE_VALUE;
		}
		return OK;
	}
}
