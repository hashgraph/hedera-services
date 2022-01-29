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

import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import javax.inject.Inject;

import static com.hedera.services.contracts.operation.HederaOperationUtil.newContractExpiryIn;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

/**
 * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.CreateOperation}.
 *
 * Addresses are allocated through {@link HederaWorldUpdater#allocateNewContractAddress(Address)}
 *
 * Gas costs are based on the expiry of the parent and the provided storage bytes per hour variable
 */
public class HederaCreateOperation extends AbstractRecordingCreateOperation {
	@Inject
	public HederaCreateOperation(
			final GasCalculator gasCalculator,
			final EntityCreator creator,
			final SyntheticTxnFactory syntheticTxnFactory,
			final AccountRecordsHistorian recordsHistorian
	) {
		super(
				0xF0,
				"ħCREATE",
				3,
				1,
				1,
				gasCalculator,
				creator,
				syntheticTxnFactory,
				recordsHistorian);
	}

	@Override
	public Gas cost(final MessageFrame frame) {
		final var effGasCalculator = gasCalculator();

		return effGasCalculator
				.createOperationGasCost(frame)
				.plus(storageAndMemoryGasForCreation(frame, effGasCalculator));
	}

	public static Gas storageAndMemoryGasForCreation(final MessageFrame frame, final GasCalculator gasCalculator) {
		final var initCodeOffset = clampedToLong(frame.getStackItem(1));
		final var initCodeLength = clampedToLong(frame.getStackItem(2));
		final var memoryGasCost = gasCalculator.memoryExpansionGasCost(frame, initCodeOffset, initCodeLength);

		final long byteHourCostInTinybars = frame.getMessageFrameStack().getLast().getContextVariable("sbh");
		final var durationInSeconds = Math.max(0, newContractExpiryIn(frame) - frame.getBlockValues().getTimestamp());
		final var gasPrice = frame.getGasPrice().toLong();

		final var storageCostTinyBars = (durationInSeconds * byteHourCostInTinybars) / 3600;
		final var storageCost = storageCostTinyBars / gasPrice;
		return Gas.of(storageCost).plus(memoryGasCost);
	}

	@Override
	protected Address targetContractAddress(final MessageFrame frame) {
		final var updater = (HederaWorldUpdater) frame.getWorldUpdater();
		final Address address = updater.allocateNewContractAddress(frame.getRecipientAddress());
		frame.warmUpAddress(address);
		return address;
	}
}
