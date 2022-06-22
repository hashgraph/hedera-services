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
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.contracts.CodeCacheProvider;
import com.hedera.services.store.contracts.HederaMutableWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.txns.contract.helpers.StorageExpiry;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;

@Singleton
public class CallEvmTxProcessor extends EvmTxProcessor {
	private final CodeCacheProvider codeCache;
	private final AliasManager aliasManager;
	private final StorageExpiry storageExpiry;

	@Inject
	public CallEvmTxProcessor(
			final HederaMutableWorldState worldState,
			final LivePricesSource livePricesSource,
			final CodeCacheProvider codeCache,
			final GlobalDynamicProperties dynamicProperties,
			final GasCalculator gasCalculator,
			final Set<Operation> hederaOperations,
			final Map<String, PrecompiledContract> precompiledContractMap,
			final AliasManager aliasManager,
			final StorageExpiry storageExpiry,
			final InHandleBlockMetaSource blockMetaSource
	) {
		super(
				worldState,
				livePricesSource,
				dynamicProperties,
				gasCalculator,
				hederaOperations,
				precompiledContractMap,
				blockMetaSource);
		this.codeCache = codeCache;
		this.aliasManager = aliasManager;
		this.storageExpiry = storageExpiry;
	}

	public TransactionProcessingResult execute(
			final Account sender,
			final Address receiver,
			final long providedGasLimit,
			final long value,
			final Bytes callData,
			final Instant consensusTime
	) {
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime, false);

		return super.execute(
				sender,
				receiver,
				gasPrice,
				providedGasLimit,
				value,
				callData,
				false,
				consensusTime,
				false,
				storageExpiry.hapiCallOracle(),
				aliasManager.resolveForEvm(receiver),
				null,
				0,
				null);
	}

	public TransactionProcessingResult executeEth(
			final Account sender,
			final Address receiver,
			final long providedGasLimit,
			final long value,
			final Bytes callData,
			final Instant consensusTime,
			final BigInteger userOfferedGasPrice,
			final Account relayer,
			final long maxGasAllowanceInTinybars
	) {
		final long gasPrice = gasPriceTinyBarsGiven(consensusTime, true);

		return super.execute(
				sender,
				receiver,
				gasPrice,
				providedGasLimit,
				value,
				callData,
				false,
				consensusTime,
				false,
				storageExpiry.hapiCallOracle(),
				aliasManager.resolveForEvm(receiver),
				userOfferedGasPrice,
				maxGasAllowanceInTinybars,
				relayer);
	}

	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCall;
	}

	@Override
	protected MessageFrame buildInitialFrame(
			final MessageFrame.Builder baseInitialFrame,
			final Address to,
			final Bytes payload,
			final long value) {
		final var code = codeCache.getIfPresent(aliasManager.resolveForEvm(to));
		/* The ContractCallTransitionLogic would have rejected a missing or deleted
		 * contract, so at this point we should have non-null bytecode available.
		 * If there is no bytecode, it means we have a non-token and non-contract account,
		 * hence the code should be null and there must be a value transfer.
		 */
		validateTrue(code != null || value > 0, ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION);

		return baseInitialFrame
				.type(MessageFrame.Type.MESSAGE_CALL)
				.address(to)
				.contract(to)
				.inputData(payload)
				.code(code == null ? Code.EMPTY : code)
				.build();
	}
}
