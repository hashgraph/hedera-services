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
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.SelfDestructOperation;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class HederaSelfDestructOperation extends SelfDestructOperation {

	public HederaSelfDestructOperation(final GasCalculator gasCalculator) {
		super(gasCalculator);
	}

	@Override
	public OperationResult execute(final MessageFrame frame, final EVM evm) {
		Address recipientAddress = Words.toAddress(frame.getStackItem(0));
		Address address = frame.getRecipientAddress();
		Account account = frame.getWorldUpdater().get(recipientAddress);
		Account beneficiaryAccount = frame.getWorldUpdater().get(recipientAddress);
		if (address.equals(recipientAddress)) {
			return new OperationResult(errorGasCost(account),
					Optional.of(HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF));
		} else if (beneficiaryAccount == null) {
			return new OperationResult(errorGasCost(null),
					Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
		} else {
			return super.execute(frame, evm);
		}
	}

	@NotNull
	private Optional<Gas> errorGasCost(final Account account) {
		final Gas cost = gasCalculator().selfDestructOperationGasCost(account, Wei.ONE);
		final Optional<Gas> optionalCost = Optional.of(cost);
		return optionalCost;
	}
}
