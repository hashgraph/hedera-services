package com.hedera.services.txns.contract.process;
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
import com.hedera.services.store.contracts.world.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.time.Instant;
import java.util.Optional;

public class CreateEvmTxProcessor extends EvmTxProcessor {

	private final Address newContractAddress;

	public CreateEvmTxProcessor(
			HbarCentExchange exchange,
			HederaWorldState.Updater worldUpdater,
			UsagePricesProvider usagePrices,
			GlobalDynamicProperties globalDynamicProperties,
			Address newContractAddress
	) {
		super(exchange, usagePrices, worldUpdater, globalDynamicProperties);
		this.newContractAddress = newContractAddress;
	}

	public TransactionProcessingResult execute(
			final Account sender,
			final long providedGasLimit,
			final long value,
			final Bytes code,
			final Instant consensusTime) {

		final long gasPrice = gasPriceTinyBarsGiven(consensusTime);
		final long gasLimit = providedGasLimit > dynamicProperties.maxGas() ? dynamicProperties.maxGas() :
				providedGasLimit;
		return super.execute(sender, Optional.empty(), gasPrice, gasLimit, value, code, true, consensusTime);
	}


	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCreate;
	}

	@Override
	protected MessageFrame buildInitialFrame(MessageFrame.Builder commonInitialFrame, Address to, Bytes payload) {
		return commonInitialFrame
				.type(MessageFrame.Type.CONTRACT_CREATION)
				.address(newContractAddress)
				.contract(newContractAddress)
				.inputData(Bytes.EMPTY)
				.code(new Code(payload))
				.build();
	}
}
