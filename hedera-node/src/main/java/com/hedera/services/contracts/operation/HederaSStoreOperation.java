package com.hedera.services.contracts.operation;

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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;

import javax.inject.Inject;
import java.util.Optional;

import static com.hedera.services.contracts.operation.HederaOperationUtil.cacheExistingValue;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;

/**
 * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.SStoreOperation} to support
 * traceability (if enabled).
 */
public class HederaSStoreOperation extends AbstractOperation {
	static final OperationResult ILLEGAL_STATE_CHANGE_RESULT = new OperationResult(
			Optional.empty(), Optional.of(ILLEGAL_STATE_CHANGE));
	public static final long FRONTIER_MINIMUM = 0L;
	public static final long EIP_1706_MINIMUM = 2300L;

	private final Gas minumumGasRemaining;
	private final GlobalDynamicProperties dynamicProperties;
	private final OperationResult insufficientMinimumGasRemainingResult;

	@Inject
	public HederaSStoreOperation(
			final long primitiveMinimumGasRemaining,
			final GasCalculator gasCalculator,
			final GlobalDynamicProperties dynamicProperties
	) {
		super(0x55, "SSTORE", 2, 0, 1, gasCalculator);
		this.dynamicProperties = dynamicProperties;

		minumumGasRemaining = Gas.of(primitiveMinimumGasRemaining);
		insufficientMinimumGasRemainingResult = new OperationResult(
				Optional.of(minumumGasRemaining), Optional.of(INSUFFICIENT_GAS));
	}

	@Override
	public OperationResult execute(final MessageFrame frame, final EVM evm) {
		final var key = UInt256.fromBytes(frame.popStackItem());
		final var value = UInt256.fromBytes(frame.popStackItem());
		final var account = frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getMutable();
		if (account == null) {
			return ILLEGAL_STATE_CHANGE_RESULT;
		}

		final var address = account.getAddress();
		final var slotIsWarm = frame.warmUpStorage(address, key);
		final var calculator = gasCalculator();
		final var gasCost = calculator
				.calculateStorageCost(account, key, value)
				.plus(slotIsWarm ? Gas.ZERO : calculator.getColdSloadCost());
		final var gasCostWrapper = Optional.of(gasCost);

		final var remainingGas = frame.getRemainingGas();
		if (frame.isStatic()) {
			return new OperationResult(gasCostWrapper, Optional.of(ILLEGAL_STATE_CHANGE));
		} else if (remainingGas.compareTo(gasCost) < 0) {
			return new OperationResult(gasCostWrapper, Optional.of(INSUFFICIENT_GAS));
		} else if (remainingGas.compareTo(minumumGasRemaining) <= 0) {
			return insufficientMinimumGasRemainingResult;
		} else {
			if (dynamicProperties.shouldEnableTraceability()) {
				cacheExistingValue(frame, address, key, account.getStorageValue(key));
			}
			frame.incrementGasRefund(calculator.calculateStorageRefundAmount(account, key, value));
			account.setStorageValue(key, value);
			frame.storageWasUpdated(key, value);
			return new OperationResult(gasCostWrapper, Optional.empty());
		}
	}

	@VisibleForTesting
	OperationResult getInsufficientMinimumGasRemainingResult() {
		return insufficientMinimumGasRemainingResult;
	}
}
