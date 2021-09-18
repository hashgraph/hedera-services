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
import com.hedera.services.store.contracts.world.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.time.Instant;
import java.util.Optional;

public class CallEvmTxProcessor extends EvmTxProcessor {

	public CallEvmTxProcessor(
			HbarCentExchange exchange,
			HederaWorldState.Updater worldUpdater,
			UsagePricesProvider usagePrices,
			GlobalDynamicProperties dynamicProperties) {
		super(exchange, usagePrices, worldUpdater, dynamicProperties);
	}

	public TransactionProcessingResult execute(
			final Account sender,
			final Account receiver,
			final long providedGasLimit,
			final long value,
			final ByteString callData,
			final Instant consensusTime
	) {
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime);
		final long gasLimit = providedGasLimit > dynamicProperties.maxGas() ? dynamicProperties.maxGas() : providedGasLimit;
		final Bytes payload = callData != null && !callData.isEmpty()
				? Bytes.fromHexString(CommonUtils.hex(callData.toByteArray())) : Bytes.EMPTY;

		return super.execute(sender, Optional.of(receiver), gasPrice, gasLimit, value, payload, false, consensusTime);
	}

	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCall;
	}

	@Override
	protected MessageFrame buildInitialFrame(MessageFrame.Builder baseInitialFrame, Address to, Bytes payload) {

		return baseInitialFrame
						.type(MessageFrame.Type.MESSAGE_CALL)
						.address(to)
						.contract(to)
						.inputData(payload)
						.code(new Code(updater.get(to).getCode()))
						.build();
	}
}
