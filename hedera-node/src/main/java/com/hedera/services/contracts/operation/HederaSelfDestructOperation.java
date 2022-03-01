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

import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
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

import javax.inject.Inject;
import java.util.Optional;
import java.util.function.BiPredicate;

/**
 * Hedera adapted version of the {@link SelfDestructOperation}.
 * <p>
 * Performs an existence check on the beneficiary {@link Address}
 * Halts the execution of the EVM transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if
 * the account does not exist or it is deleted.
 * <p>
 * Halts the execution of the EVM transaction with {@link HederaExceptionalHaltReason#SELF_DESTRUCT_TO_SELF} if the
 * beneficiary address is the same as the address being destructed
 */
public class HederaSelfDestructOperation extends SelfDestructOperation {

	private final BiPredicate<Address, MessageFrame> addressValidator;

	@Inject
	public HederaSelfDestructOperation(final GasCalculator gasCalculator,
									   final BiPredicate<Address, MessageFrame> addressValidator) {
		super(gasCalculator);
		this.addressValidator = addressValidator;
	}

	@Override
	public OperationResult execute(final MessageFrame frame, final EVM evm) {
		final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
		final var beneficiaryAddressOrAlias = Words.toAddress(frame.getStackItem(0));
		final var beneficiaryAddress = updater.priorityAddress(beneficiaryAddressOrAlias);
		if (!addressValidator.test(beneficiaryAddress, frame)) {
			return new OperationResult(errorGasCost(null),
					Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
		}

		// This address is already the EIP-1014 address if applicable; so we can compare it directly to
		// the "priority" address we computed above for the beneficiary
		final var address = frame.getRecipientAddress();
		if (address.equals(beneficiaryAddress)) {
			final var account = frame.getWorldUpdater().get(beneficiaryAddress);
			return new OperationResult(errorGasCost(account),
					Optional.of(HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF));
		} else {
			return super.execute(frame, evm);
		}
	}

	@NotNull
	private Optional<Gas> errorGasCost(final Account account) {
		final Gas cost = gasCalculator().selfDestructOperationGasCost(account, Wei.ONE);
		return Optional.of(cost);
	}
}
