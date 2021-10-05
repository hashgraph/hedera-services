package com.hedera.services.txns.contract.operation;

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

import com.hedera.services.store.contracts.HederaWorldState.WorldStateAccount;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractCreateOperation;

import java.util.HashMap;

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

public class HederaCreateOperation extends AbstractCreateOperation {

	public HederaCreateOperation(final GasCalculator gasCalculator) {
		super(0xF0, "ħCREATE", 3, 1, false, 1, gasCalculator);
	}

	@Override
	public Gas cost(final MessageFrame frame) {
		final long initCodeOffset = clampedToLong(frame.getStackItem(1));
		final long initCodeLength = clampedToLong(frame.getStackItem(2));

		//TODO charge for memory expansion cost?
		//final Gas memoryGasCost = gasCalculator().memoryExpansionGasCost(frame, initCodeOffset, initCodeLength);
		HashMap<Address, Long> map = frame.getMessageFrameStack().getLast().getContextVariable("expiries");
		long expiry = 0;
		if (!map.containsKey(frame.getSenderAddress())) {
			WorldStateAccount hederaAccount =
					((HederaWorldUpdater) frame.getWorldUpdater()).getHederaAccount(frame.getRecipientAddress());
			if (hederaAccount == null) {
				hederaAccount = ((HederaWorldUpdater) frame.getWorldUpdater()).getHederaAccount(frame.getSenderAddress());
				expiry = hederaAccount.getExpiry();
			}
		} else {
			expiry = map.get(frame.getSenderAddress());
		}

		long numberOfBytes = initCodeLength - initCodeOffset;
		long byteHourCostInTinybars = frame.getMessageFrameStack().getLast().getContextVariable("rbh");
		long durationInSeconds = Math.max(0, expiry - frame.getBlockValues().getTimestamp());
		long gasPrice = frame.getGasPrice().toLong();
		map.put(frame.getRecipientAddress(), expiry);

		long storageCost = 0;
		long bps = durationInSeconds * numberOfBytes;
		long storageCostTinyBars = (durationInSeconds * byteHourCostInTinybars) / 3600;
		storageCost = Math.round((double) storageCostTinyBars / (double) gasPrice);
		return Gas.of(storageCost); //.plus(memoryGasCost)
	}

	@Override
	protected Address targetContractAddress(final MessageFrame frame) {
		final Address address = ((HederaWorldUpdater) frame.getWorldUpdater()).allocateNewContractAddress(
				frame.getRecipientAddress());
		frame.warmUpAddress(address);
		return address;
	}
}
