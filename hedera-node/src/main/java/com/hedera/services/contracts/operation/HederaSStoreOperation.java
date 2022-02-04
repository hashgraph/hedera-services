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

import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV18;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;

import javax.inject.Inject;
import java.util.Optional;

/**
 * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.SStoreOperation}.
 * Gas costs are based on the expiry of the current or parent account and the provided storage bytes per hour variable
 */
public class HederaSStoreOperation extends AbstractOperation {

	protected static final Operation.OperationResult ILLEGAL_STATE_CHANGE =
			new Operation.OperationResult(
					Optional.empty(), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));

	private final boolean checkSuperCost;

	@Inject
	public HederaSStoreOperation(final GasCalculator gasCalculator) {
		super(0x55, "SSTORE", 2, 0, 1, gasCalculator);
		checkSuperCost = !(gasCalculator instanceof GasCalculatorHederaV18);
	}

	@Override
	public Operation.OperationResult execute(final MessageFrame frame, final EVM evm) {
		final UInt256 key = UInt256.fromBytes(frame.popStackItem());
		final UInt256 value = UInt256.fromBytes(frame.popStackItem());

		final MutableAccount account =
				frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getMutable();
		if (account == null) {
			return ILLEGAL_STATE_CHANGE;
		}

		UInt256 currentValue = account.getStorageValue(key);
		boolean currentZero = currentValue.isZero();
		boolean newZero = value.isZero();
		boolean checkCalculator = checkSuperCost;
		Gas gasCost = Gas.ZERO;
		if (currentZero && !newZero) {
			HederaWorldState.WorldStateAccount hederaAccount =
					((HederaWorldUpdater) frame.getWorldUpdater()).getHederaAccount(frame.getRecipientAddress());
			long durationInSeconds = Math.max(0,
					(hederaAccount != null ? hederaAccount.getExpiry() : HederaOperationUtil.computeExpiryForNewContract(frame))
							- frame.getBlockValues().getTimestamp());
			long sbh = frame.getMessageFrameStack().getLast().getContextVariable("sbh");

			Wei gasPrice = frame.getGasPrice();
			gasCost = Gas.of(calculateStorageGasNeeded(
					64 /*two 256-bit words*/, durationInSeconds, sbh, gasPrice.toLong()));
			((HederaWorldUpdater)frame.getWorldUpdater()).addSbhRefund(gasCost);
		} else {
			checkCalculator = true;
		}

		if (checkCalculator) {
			Address address = account.getAddress();
			boolean slotIsWarm = frame.warmUpStorage(address, key);
			gasCost = gasCost.max(gasCalculator().calculateStorageCost(account, key, value)
					.plus(slotIsWarm ? Gas.ZERO : this.gasCalculator().getColdSloadCost()));
			frame.incrementGasRefund(gasCalculator().calculateStorageRefundAmount(account, key, value));
		}

		Optional<Gas> optionalCost = Optional.of(gasCost);
		final Gas remainingGas = frame.getRemainingGas();
		if (frame.isStatic()) {
			return new OperationResult(
					optionalCost, Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
		} else if (remainingGas.compareTo(gasCost) < 0) {
			return new OperationResult(optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
		}

		HederaOperationUtil.setOriginalReadValue(frame, account.getAddress(), key, currentValue);
		account.setStorageValue(key, value);
		frame.storageWasUpdated(key, value);
		return new Operation.OperationResult(optionalCost, Optional.empty());
	}

	@SuppressWarnings("unused")
	public static long calculateStorageGasNeeded(
			long numberOfBytes,
			long durationInSeconds,
			long byteHourCostIntinybars,
			long gasPrice
	) {
		long storageCostTinyBars = (durationInSeconds * byteHourCostIntinybars) / 3600;
		return Math.round((double) storageCostTinyBars / (double) gasPrice);
	}
}
