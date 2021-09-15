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

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Optional;

@Singleton
public class CallEvmTxProcessor extends EvmTxProcessor {

	@Inject
	public CallEvmTxProcessor(
			HbarCentExchange exchange,
			HederaWorldUpdater worldUpdater,
			UsagePricesProvider usagePrices,
			GlobalDynamicProperties dynamicProperties) {
		super(exchange, worldUpdater, usagePrices, dynamicProperties);
	}

	public void execute(
			final Account sender,
			final Account receiver,
			final long providedGasLimit,
			final long value,
			final ByteString callData,
			final Instant consensusTime
	) {
		final Wei gasPrice = Wei.of(gasPriceTinyBarsGiven(consensusTime));
		final long gasLimit = providedGasLimit > dynamicProperties.maxGas() ? dynamicProperties.maxGas() : providedGasLimit;
		final Bytes payload = callData != null && !callData.isEmpty()
				? Bytes.fromHexString(CommonUtils.hex(callData.toByteArray())) : Bytes.EMPTY;

		final var transaction = new Transaction(
				0,
				gasPrice,
				gasLimit,
				Optional.of(receiver.getId().asEvmAddress()),
				Wei.of(value),
				null,
				payload,
				sender.getId().asEvmAddress(),
				Optional.empty());
		super.execute(sender, transaction, consensusTime);
	}

	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCall;
	}

	@Override
	protected MessageFrame buildInitialFrame(MessageFrame.Builder baseInitialFrame, Transaction transaction) {
		final Address to = transaction.getTo().get();
		return baseInitialFrame
						.type(MessageFrame.Type.MESSAGE_CALL)
						.address(to)
						.contract(to)
						.inputData(transaction.getPayload())
						.code(new Code(worldState.get(to).getCode()))
						.build();
	}
}
