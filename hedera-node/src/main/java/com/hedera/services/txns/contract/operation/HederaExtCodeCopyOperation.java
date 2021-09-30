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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.ExtCodeCopyOperation;

import java.util.Optional;

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

public class HederaExtCodeCopyOperation extends ExtCodeCopyOperation {
	public HederaExtCodeCopyOperation(GasCalculator gasCalculator) {
		super(gasCalculator);
	}

	@Override
	public OperationResult execute(MessageFrame frame, EVM evm) {
		final Address address = Words.toAddress(frame.getStackItem(0));
		final long memOffset = clampedToLong(frame.getStackItem(1));
		final long numBytes = clampedToLong(frame.getStackItem(3));

		final var account = frame.getWorldUpdater().get(address);
		if (account == null) {
			return new OperationResult(
					Optional.of(cost(frame, memOffset, numBytes, true)), Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
		}

		return super.execute(frame, evm);
	}
}
