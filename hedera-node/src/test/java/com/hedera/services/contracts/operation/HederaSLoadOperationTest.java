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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.Optional;

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.TOO_MANY_STACK_ITEMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class HederaSLoadOperationTest {
	final Address recipientAccount = Address.fromHexString("0x0001");

	HederaSLoadOperation subject;

	@Mock
	GasCalculator gasCalculator;

	@Mock
	MessageFrame messageFrame;

	@Mock
	EVM evm;

	@Mock
	HederaStackedWorldStateUpdater worldUpdater;

	@Mock
	EvmAccount evmAccount;

	@Mock
	Bytes keyBytesMock;

	@Mock
	Bytes valueBytesMock;

	@Mock
	private GlobalDynamicProperties dynamicProperties;

	@BeforeEach
	void setUp() {
		givenValidContext();
		subject = new HederaSLoadOperation(gasCalculator, dynamicProperties);
	}

	@Test
	void executesProperlyWithColdSuccess() {
		givenAdditionalContext(keyBytesMock, valueBytesMock);
		given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
		given(messageFrame.getRemainingGas()).willReturn(Gas.of(300));
		given(messageFrame.warmUpStorage(any(), any())).willReturn(false);
		given(dynamicProperties.shouldEnableTraceability()).willReturn(true);

		var frameStack = new ArrayDeque<MessageFrame>();
		frameStack.add(messageFrame);

		given(messageFrame.getMessageFrameStack()).willReturn(frameStack);
		final var coldResult = subject.execute(messageFrame, evm);

		final var expectedColdResult = new Operation.OperationResult(Optional.of(Gas.of(20L)), Optional.empty());

		assertEquals(expectedColdResult.getGasCost(), coldResult.getGasCost());
		assertEquals(expectedColdResult.getHaltReason(), coldResult.getHaltReason());
		assertEquals(expectedColdResult.getPcIncrement(), coldResult.getPcIncrement());

		// TODO: add verify statements
	}

	@Test
	void executesProperlyWithWarmSuccess() {
		givenAdditionalContext(keyBytesMock, valueBytesMock);
		given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
		given(messageFrame.getRemainingGas()).willReturn(Gas.of(300));
		given(dynamicProperties.shouldEnableTraceability()).willReturn(true);
		var frameStack = new ArrayDeque<MessageFrame>();
		frameStack.add(messageFrame);

		given(messageFrame.getMessageFrameStack()).willReturn(frameStack);
		final var warmResult = subject.execute(messageFrame, evm);

		final var expectedWarmResult = new Operation.OperationResult(Optional.of(Gas.of(30L)), Optional.empty());

		assertEquals(expectedWarmResult.getGasCost(), warmResult.getGasCost());
		assertEquals(expectedWarmResult.getHaltReason(), warmResult.getHaltReason());
		assertEquals(expectedWarmResult.getPcIncrement(), warmResult.getPcIncrement());

		// TODO: add verify statements
	}

	@Test
	void executeHaltsForInsufficientGas() {
		givenAdditionalContext(keyBytesMock, valueBytesMock);
		given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
		given(messageFrame.getRemainingGas()).willReturn(Gas.of(300));
		given(messageFrame.getRemainingGas()).willReturn(Gas.of(0));

		final var expectedHaltResult = new Operation.OperationResult(Optional.of(Gas.of(30L)),
				Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

		final var haltResult = subject.execute(messageFrame, evm);

		assertEquals(expectedHaltResult.getGasCost(), haltResult.getGasCost());
		assertEquals(expectedHaltResult.getHaltReason(), haltResult.getHaltReason());
		assertEquals(expectedHaltResult.getPcIncrement(), haltResult.getPcIncrement());
	}

	@Test
	void executeHaltsForWrongAddressOfAliasedContract() {
		given(worldUpdater.isInconsistentMirrorAddress(any())).willReturn(true);

		final var expectedHaltResult = new Operation.OperationResult(Optional.of(Gas.of(20L)),
				Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));

		final var haltResult = subject.execute(messageFrame, evm);

		assertEquals(expectedHaltResult.getGasCost(), haltResult.getGasCost());
		assertEquals(expectedHaltResult.getHaltReason(), haltResult.getHaltReason());
		assertEquals(expectedHaltResult.getPcIncrement(), haltResult.getPcIncrement());
	}

	@Test
	void executeWithUnderFlowException() {
		givenAdditionalContext(keyBytesMock, valueBytesMock);
		given(messageFrame.popStackItem()).willThrow(new FixedStack.UnderflowException());
		final var result = subject.execute(messageFrame, evm);
		assertEquals(INSUFFICIENT_STACK_ITEMS, result.getHaltReason().get());
	}

	@Test
	void executeWithOverFlowException() {
		givenAdditionalContext(keyBytesMock, valueBytesMock);
		given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
		given(messageFrame.getRemainingGas()).willReturn(Gas.of(300));
		given(dynamicProperties.shouldEnableTraceability()).willReturn(true);
		var frameStack = new ArrayDeque<MessageFrame>();
		frameStack.add(messageFrame);

		given(messageFrame.getMessageFrameStack()).willReturn(frameStack);
		doThrow(new FixedStack.OverflowException()).when(messageFrame).pushStackItem(any());

		final var result = subject.execute(messageFrame, evm);
		assertEquals(TOO_MANY_STACK_ITEMS, result.getHaltReason().get());
	}

	private void givenAdditionalContext(Bytes key, Bytes value) {
		final UInt256 keyBytes = UInt256.fromBytes(key);
		final UInt256 valueBytes = UInt256.fromBytes(value);

		given(messageFrame.popStackItem()).willReturn(keyBytes).willReturn(valueBytes);
		given(worldUpdater.get(recipientAccount)).willReturn(evmAccount);
		given(evmAccount.getAddress()).willReturn(Address.fromHexString("0x123"));
	}

	private void givenValidContext() {
		given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
		given(messageFrame.getRecipientAddress()).willReturn(recipientAccount);

		given(gasCalculator.getSloadOperationGasCost()).willReturn(Gas.of(10));
		given(gasCalculator.getWarmStorageReadCost()).willReturn(Gas.of(20));
		given(gasCalculator.getColdSloadCost()).willReturn(Gas.of(10));
	}
}
