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
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.ExtCodeSizeOperation;

import javax.inject.Inject;
import java.util.Optional;

public class HederaExtCodeSizeOperation extends ExtCodeSizeOperation {

	@Inject
	public HederaExtCodeSizeOperation(GasCalculator gasCalculator) {
		super(gasCalculator);
	}

	@Override
	public OperationResult execute(MessageFrame frame, EVM evm) {
		try {
			final Address address = Words.toAddress(frame.getStackItem(0));
			final var account = frame.getWorldUpdater().get(address);
			if (account == null) {
				return new OperationResult(
						Optional.of(cost(true)), Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
			}

			return super.execute(frame, evm);
		} catch (final FixedStack.UnderflowException ufe) {
			return new OperationResult(
					Optional.of(cost(true)), Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
		}
	}
}
