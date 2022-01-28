package com.hedera.services.contracts.operation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.store.contracts.HederaWorldState;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HederaSLoadOperationTest {
	HederaSLoadOperation subject;

	@Mock
	GasCalculator gasCalculator;

	@Mock
	MessageFrame messageFrame;

	@Mock
	EVM evm;

	@Mock
	HederaWorldState.Updater worldUpdater;

	@Mock
	EvmAccount evmAccount;

	@Mock
	Bytes keyBytesMock;

	@Mock
	Bytes valueBytesMock;

	@BeforeEach
	void setUp() {
		givenValidContext(keyBytesMock, valueBytesMock);
		subject = new HederaSLoadOperation(gasCalculator);
	}

	@Test
	void executesProperlyWithColdSuccess() {
		given(messageFrame.warmUpStorage(any(), any())).willReturn(false);
		given(worldUpdater.getStorageChanges()).willReturn(new TreeMap<>());
		final var coldResult = subject.execute(messageFrame, evm);

		final var expectedColdResult = new Operation.OperationResult(Optional.of(Gas.of(20L)), Optional.empty());

		assertEquals(expectedColdResult.getGasCost(), coldResult.getGasCost());
		assertEquals(expectedColdResult.getHaltReason(), coldResult.getHaltReason());
		assertEquals(expectedColdResult.getPcIncrement(), coldResult.getPcIncrement());

		// TODO: add verify statements
	}

	@Test
	void executesProperlyWithWarmSuccess() {
		given(worldUpdater.getStorageChanges()).willReturn(new TreeMap<>());
		final var warmResult = subject.execute(messageFrame, evm);

		final var expectedWarmResult = new Operation.OperationResult(Optional.of(Gas.of(30L)), Optional.empty());

		assertEquals(expectedWarmResult.getGasCost(), warmResult.getGasCost());
		assertEquals(expectedWarmResult.getHaltReason(), warmResult.getHaltReason());
		assertEquals(expectedWarmResult.getPcIncrement(), warmResult.getPcIncrement());

		// TODO: add verify statements
	}

	@Test
	void executeHaltsForInsufficientGas() {
		given(messageFrame.getRemainingGas()).willReturn(Gas.of(0));

		final var expectedHaltResult = new Operation.OperationResult(Optional.of(Gas.of(20L)),
				Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

		final var haltResult = subject.execute(messageFrame, evm);

		assertEquals(expectedHaltResult.getGasCost(), haltResult.getGasCost());
		assertEquals(expectedHaltResult.getHaltReason(), haltResult.getHaltReason());
		assertEquals(expectedHaltResult.getPcIncrement(), haltResult.getPcIncrement());
	}

	private void givenValidContext(Bytes key, Bytes value) {
		final UInt256 keyBytes = UInt256.fromBytes(key);
		final UInt256 valueBytes = UInt256.fromBytes(value);
		final var recipientAccount = Address.fromHexString("0x0001");

		given(messageFrame.popStackItem()).willReturn(keyBytes).willReturn(valueBytes);
		given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
		given(messageFrame.getRecipientAddress()).willReturn(recipientAccount);
		given(worldUpdater.get(recipientAccount)).willReturn(evmAccount);

		given(evmAccount.getAddress()).willReturn(Address.fromHexString("0x123"));
		given(gasCalculator.getSloadOperationGasCost()).willReturn(Gas.of(10));
		given(gasCalculator.getWarmStorageReadCost()).willReturn(Gas.of(20));
		given(gasCalculator.getColdSloadCost()).willReturn(Gas.of(10));
		given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
		given(messageFrame.getRemainingGas()).willReturn(Gas.of(300));
	}
}
