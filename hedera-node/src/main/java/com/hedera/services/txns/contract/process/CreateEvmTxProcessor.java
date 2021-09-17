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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
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

		final Wei gasPrice = Wei.of(gasPriceTinyBarsGiven(consensusTime));
		final long gasLimit = providedGasLimit > dynamicProperties.maxGas() ? dynamicProperties.maxGas() : providedGasLimit;
		var transaction = new Transaction(
				0,
				gasPrice,
				gasLimit,
				Optional.empty(),
				Wei.of(value),
				null,
				code,
				sender.getId().asEvmAddress(),
				Optional.empty());
		return super.execute(sender, transaction, consensusTime);
	}


	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCreate;
	}

	@Override
	protected MessageFrame buildInitialFrame(MessageFrame.Builder commonInitialFrame, Transaction transaction) {
		return commonInitialFrame
						.type(MessageFrame.Type.CONTRACT_CREATION)
						.address(newContractAddress)
						.contract(newContractAddress)
						.inputData(Bytes.EMPTY)
						.code(new Code(transaction.getPayload()))
						.build();
	}
}
