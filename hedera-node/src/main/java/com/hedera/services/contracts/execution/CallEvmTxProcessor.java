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
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

@Singleton
public class CallEvmTxProcessor extends EvmTxProcessor {
	private final CodeCache codeCache;

	@Inject
	public CallEvmTxProcessor(
			final HederaMutableWorldState worldState,
			final LivePricesSource livePricesSource,
			final CodeCache codeCache,
			final GlobalDynamicProperties dynamicProperties,
			final GasCalculator gasCalculator,
			final Set<Operation> hederaOperations,
			final Map<String, PrecompiledContract> precompiledContractMap
	) {
		super(worldState, livePricesSource, dynamicProperties, gasCalculator, hederaOperations, precompiledContractMap);
		this.codeCache = codeCache;
	}

	public TransactionProcessingResult execute(
			final Account sender,
			final Address receiver,
			final long providedGasLimit,
			final long value,
			final Bytes callData,
			final Instant consensusTime
	) {
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime);

		return super.execute(sender,
				receiver,
				gasPrice,
				providedGasLimit,
				value,
				callData,
				false,
				consensusTime,
				false,
				OptionalLong.empty());
	}

	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCall;
	}

	@Override
	protected MessageFrame buildInitialFrame(
			final MessageFrame.Builder baseInitialFrame,
			final HederaWorldUpdater updater,
			final Address to,
			final Bytes payload
	) {
		final var code = codeCache.getIfPresent(to);
		/* The ContractCallTransitionLogic would have rejected a missing or deleted
		* contract, so at this point we should have non-null bytecode available. */
		validateTrue(code != null, FAIL_INVALID);

		return baseInitialFrame
				.type(MessageFrame.Type.MESSAGE_CALL)
				.address(to)
				.contract(to)
				.inputData(payload)
				.code(code)
				.build();
	}
}
