package com.hedera.services.contracts.execution;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Extension of the base {@link EvmTxProcessor} that provides interface for executing
 * {@link com.hederahashgraph.api.proto.java.ContractCreateTransactionBody} transactions
 */
@Singleton
public class CreateEvmTxProcessor extends EvmTxProcessor {

	@Inject
	public CreateEvmTxProcessor(
			HederaWorldState worldState,
			HbarCentExchange exchange,
			UsagePricesProvider usagePrices,
			GlobalDynamicProperties globalDynamicProperties,
			GasCalculator gasCalculator,
			Set<Operation> hederaOperations,
			Map<String, PrecompiledContract> precompiledContractMap) {
		super(worldState, exchange, usagePrices, globalDynamicProperties, gasCalculator, hederaOperations, precompiledContractMap);
	}

	public TransactionProcessingResult execute(
			final Account sender,
			final Address receiver,
			final long providedGasLimit,
			final long value,
			final Bytes code,
			final Instant consensusTime,
			final long expiry) {
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime);

		return super.execute(sender, receiver, gasPrice, providedGasLimit, value, code, true, consensusTime, false, Optional.of(expiry));
	}


	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCreate;
	}

	@Override
	protected MessageFrame buildInitialFrame(MessageFrame.Builder commonInitialFrame, HederaWorldState.Updater updater, Address to, Bytes payload) {
		return commonInitialFrame
				.type(MessageFrame.Type.CONTRACT_CREATION)
				.address(to)
				.contract(to)
				.inputData(Bytes.EMPTY)
				.code(new Code(payload, Hash.hash(payload)))
				.build();
	}
}
