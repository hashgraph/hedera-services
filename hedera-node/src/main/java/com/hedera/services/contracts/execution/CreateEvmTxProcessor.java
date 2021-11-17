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
import java.util.OptionalLong;
import java.util.Set;

/**
 * Extension of the base {@link EvmTxProcessor} that provides interface for executing
 * {@link com.hederahashgraph.api.proto.java.ContractCreateTransactionBody} transactions
 */
@Singleton
public class CreateEvmTxProcessor extends EvmTxProcessor {
	private CodeCache codeCache;

	@Inject
	public CreateEvmTxProcessor(
			final HederaMutableWorldState worldState,
			final LivePricesSource livePricesSource,
			final CodeCache codeCache,
			final GlobalDynamicProperties globalDynamicProperties,
			final GasCalculator gasCalculator,
			final Set<Operation> hederaOperations,
			final Map<String, PrecompiledContract> precompiledContractMap
	) {
		super(worldState, livePricesSource, globalDynamicProperties, gasCalculator, hederaOperations, precompiledContractMap);
		this.codeCache = codeCache;
	}

	public TransactionProcessingResult execute(
			final Account sender,
			final Address receiver,
			final long providedGasLimit,
			final long value,
			final Bytes code,
			final Instant consensusTime,
			final long expiry
	) {
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime);

		return super.execute(
				sender,
				receiver,
				gasPrice,
				providedGasLimit,
				value,
				code,
				true,
				consensusTime,
				false,
				OptionalLong.of(expiry));
	}

	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCreate;
	}

	@Override
	protected MessageFrame buildInitialFrame(
			final MessageFrame.Builder commonInitialFrame,
			final HederaWorldUpdater updater,
			final Address to,
			final Bytes payload
	) {
		// Must invalidate cache for contract address to avoid ISS when a contract create +
		// call are performed in the same round. Scenario that could cause issue:
		//
		// 1. pre-fetch ContractCall - tries to find bytes, caches Bytes.EMPTY
		// 2. handle ContractCreate - populates bytes into storage
		// 3. handle ContractCall - fetches bytes from cache, receives Bytes.EMPTY
		//
		// Cache invalidation will cause (3) to retrieve the correct bytes.
		//
		codeCache.invalidate(to);

		return commonInitialFrame
				.type(MessageFrame.Type.CONTRACT_CREATION)
				.address(to)
				.contract(to)
				.inputData(Bytes.EMPTY)
				.code(new Code(payload, Hash.hash(payload)))
				.build();
	}
}
