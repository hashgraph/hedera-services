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
import com.hedera.services.store.contracts.HederaWorldUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractCreateOperation;

import javax.inject.Inject;

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

/**
 * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.CreateOperation}.
 *
 * Addresses are allocated through {@link HederaWorldUpdater#allocateNewContractAddress(Address)}
 *
 * Gas costs are based on the expiry of the parent and the provided storage bytes per hour variable
 */
public class HederaCreateOperation extends AbstractCreateOperation {
	boolean checkSuperCost;

	@Inject
	public HederaCreateOperation(final GasCalculator gasCalculator) {
		super(0xF0, "ħCREATE", 3, 1, 1, gasCalculator);
		checkSuperCost = !(gasCalculator instanceof GasCalculatorHederaV18);
	}

	@Override
	public Gas cost(final MessageFrame frame) {
		final long initCodeOffset = clampedToLong(frame.getStackItem(1));
		final long initCodeLength = clampedToLong(frame.getStackItem(2));

		final Gas memoryGasCost = gasCalculator().memoryExpansionGasCost(frame, initCodeOffset, initCodeLength);

		long byteHourCostInTinybars = frame.getMessageFrameStack().getLast().getContextVariable("sbh");
		long durationInSeconds = Math.max(0, HederaOperationUtil.computeExpiryForNewContract(frame) - frame.getBlockValues().getTimestamp());
		long gasPrice = frame.getGasPrice().toLong();

		long storageCostTinyBars = (durationInSeconds * byteHourCostInTinybars) / 3600;
		long storageCost = Math.round((double) storageCostTinyBars / (double) gasPrice);

		Gas gasCost = gasCalculator().createOperationGasCost(frame).plus(Gas.of(storageCost).plus(memoryGasCost));
		if (checkSuperCost) {
			return gasCost.max(this.gasCalculator().createOperationGasCost(frame));
		} else {
			return gasCost;
		}
	}

	@Override
	protected Address targetContractAddress(final MessageFrame frame) {
		final Address address = ((HederaWorldUpdater) frame.getWorldUpdater()).allocateNewContractAddress(
				frame.getRecipientAddress());
		frame.warmUpAddress(address);
		return address;
	}
}
