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
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.Set;

/**
 * Extension of the base {@link EvmTxProcessor} that provides interface for
 * executing {@link com.hederahashgraph.api.proto.java.ContractCallLocal} queries
 */
@Singleton
public class CallLocalEvmTxProcessor extends EvmTxProcessor {
	private static final Logger logger = LogManager.getLogger(CallLocalEvmTxProcessor.class);

	private CodeCache codeCache;

	@Inject
	public CallLocalEvmTxProcessor(
			final CodeCache codeCache,
			final LivePricesSource livePricesSource,
			final GlobalDynamicProperties dynamicProperties,
			final GasCalculator gasCalculator,
			final Set<Operation> hederaOperations
	) {
		super(livePricesSource, dynamicProperties, gasCalculator, hederaOperations);
		this.codeCache = codeCache;
	}

	@Override
	public void setWorldState(HederaMutableWorldState worldState) {
		super.setWorldState(worldState);
	}

	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCallLocal;
	}

	public TransactionProcessingResult execute(
			final Account sender,
			final Address receiver,
			final long providedGasLimit,
			final long value,
			final Bytes callData,
			final Instant consensusTime
	) {
		final long gasPrice = 1;

		return super.execute(sender,
				receiver,
				gasPrice,
				providedGasLimit,
				value,
				callData,
				false,
				consensusTime,
				true,
				OptionalLong.empty());
	}

	@Override
	protected MessageFrame buildInitialFrame(
			final MessageFrame.Builder baseInitialFrame,
			final HederaWorldUpdater updater,
			final Address to,
			final Bytes payload
	) {
		try {
			Code code = codeCache.get(to);
			return baseInitialFrame
					.type(MessageFrame.Type.MESSAGE_CALL)
					.address(to)
					.contract(to)
					.inputData(payload)
					.code(code)
					.build();
		} catch (RuntimeException e) {
			logger.warn("Error fetching code from cache", e);
			throw new InvalidTransactionException(ResponseCodeEnum.FAIL_INVALID);
		}
	}
}
