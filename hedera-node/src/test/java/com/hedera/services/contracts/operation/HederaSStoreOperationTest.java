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

import com.hedera.services.store.contracts.HederaWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class HederaSStoreOperationTest {
	HederaSStoreOperation subject;

	@Mock
	GasCalculator gasCalculator;

	@Mock
	MessageFrame messageFrame;

	@Mock
	EVM evm;

	@Mock
	HederaWorldUpdater worldUpdater;

	@Mock
	MutableAccount mutableAccount;

	@Mock
	EvmAccount evmAccount;

	@Mock
	Bytes keyBytesMock;

	@Mock
	Bytes valueBytesMock;

	@Mock
	BlockValues hederaBlockValues;

	@BeforeEach
	void setUp() {
		subject = new HederaSStoreOperation(gasCalculator);
	}

	@Test
	void executesCorrectly() {
		givenValidContext(keyBytesMock, valueBytesMock);

		final var result = subject.execute(messageFrame, evm);

		final var expected = new Operation.OperationResult(Optional.of(Gas.of(10)), Optional.empty());

		assertEquals(expected.getGasCost(), result.getGasCost());
		assertEquals(expected.getHaltReason(), result.getHaltReason());

		verify(mutableAccount).setStorageValue(any(), any());
		verify(messageFrame).storageWasUpdated(any(), any());
	}

	@Test
	void haltsWithIllegalStateChange() {
		givenValidContext(keyBytesMock, valueBytesMock);

		given(messageFrame.isStatic()).willReturn(true);

		final var result = subject.execute(messageFrame, evm);

		final var expected = new Operation.OperationResult(Optional.of(Gas.of(10)), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));

		assertEquals(expected.getGasCost(), result.getGasCost());
		assertEquals(expected.getHaltReason(), result.getHaltReason());

		verify(mutableAccount, never()).setStorageValue(any(), any());
		verify(messageFrame, never()).storageWasUpdated(any(), any());
	}

	@Test
	void haltsWithInsufficientGas() {
		final UInt256 keyBytes = UInt256.fromBytes(keyBytesMock);
		final UInt256 valueBytes = UInt256.fromBytes(valueBytesMock);
		final var recipientAccount = Address.fromHexString("0x0001");

		given(messageFrame.popStackItem()).willReturn(keyBytes).willReturn(valueBytes);
		given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
		given(messageFrame.getRecipientAddress()).willReturn(recipientAccount);
		given(worldUpdater.getAccount(recipientAccount)).willReturn(evmAccount);
		given(evmAccount.getMutable()).willReturn(mutableAccount);
		given(mutableAccount.getStorageValue(any())).willReturn(keyBytes);
		given(gasCalculator.calculateStorageCost(any(), any(), any())).willReturn(Gas.of(10));
		given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
		given(messageFrame.isStatic()).willReturn(false);
		given(messageFrame.getRemainingGas()).willReturn(Gas.of(0));

		final var result = subject.execute(messageFrame, evm);

		final var expected = new Operation.OperationResult(Optional.of(Gas.of(10)), Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

		assertEquals(expected.getGasCost(), result.getGasCost());
		assertEquals(expected.getHaltReason(), result.getHaltReason());

		verify(mutableAccount, never()).setStorageValue(any(), any());
		verify(messageFrame, never()).storageWasUpdated(any(), any());
	}

	@Test
	void haltsWhenMutableAccountIsUnavailable() {
		final UInt256 keyBytes = UInt256.fromBytes(keyBytesMock);
		final UInt256 valueBytes = UInt256.fromBytes(valueBytesMock);
		final var recipientAccount = Address.fromHexString("0x0001");

		given(messageFrame.popStackItem()).willReturn(keyBytes).willReturn(valueBytes);
		given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
		given(messageFrame.getRecipientAddress()).willReturn(recipientAccount);
		given(worldUpdater.getAccount(recipientAccount)).willReturn(evmAccount);

		final var result = subject.execute(messageFrame, evm);

		final var expected = new Operation.OperationResult(
				Optional.empty(), Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));

		assertEquals(expected.getGasCost(), result.getGasCost());
		assertEquals(expected.getHaltReason(), result.getHaltReason());

		verify(mutableAccount, never()).setStorageValue(any(), any());
		verify(messageFrame, never()).storageWasUpdated(any(), any());
	}

	@Test
	void executesWithZero() {
		final UInt256 keyBytes = UInt256.fromBytes(keyBytesMock);
		final UInt256 valueBytes = UInt256.fromBytes(Bytes.fromHexString("0x12345678"));

		givenValidContext(keyBytes, valueBytes);
		given(mutableAccount.getStorageValue(any())).willReturn(UInt256.ZERO);

		final var expectedExpiry = 20L;
		Deque<MessageFrame> frameDeque = new ArrayDeque<>();
		frameDeque.add(messageFrame);
		given(messageFrame.getMessageFrameStack()).willReturn(frameDeque);
		given(messageFrame.getContextVariable("expiry")).willReturn(OptionalLong.of(expectedExpiry));
		given(messageFrame.getContextVariable("sbh")).willReturn(5L);
		given(messageFrame.getBlockValues()).willReturn(hederaBlockValues);
		given(messageFrame.getGasPrice()).willReturn(Wei.of(50000L));
		given(hederaBlockValues.getTimestamp()).willReturn(10L);

		final var result = subject.execute(messageFrame, evm);

		final var expected = new Operation.OperationResult(Optional.of(Gas.of(10)), Optional.empty());

		assertEquals(expected.getGasCost(), result.getGasCost());
		assertEquals(expected.getHaltReason(), result.getHaltReason());

		verify(mutableAccount).setStorageValue(any(), any());
		verify(messageFrame).storageWasUpdated(any(), any());
	}

	private void givenValidContext(Bytes key, Bytes value) {
		final UInt256 keyBytes = UInt256.fromBytes(key);
		final UInt256 valueBytes = UInt256.fromBytes(value);
		final var recipientAccount = Address.fromHexString("0x0001");

		given(messageFrame.popStackItem()).willReturn(keyBytes).willReturn(valueBytes);
		given(messageFrame.getWorldUpdater()).willReturn(worldUpdater);
		given(messageFrame.getRecipientAddress()).willReturn(recipientAccount);
		given(worldUpdater.getAccount(recipientAccount)).willReturn(evmAccount);
		given(evmAccount.getMutable()).willReturn(mutableAccount);
		given(gasCalculator.calculateStorageCost(any(), any(), any())).willReturn(Gas.of(10));
		given(messageFrame.warmUpStorage(any(), any())).willReturn(true);
		given(messageFrame.isStatic()).willReturn(false);
		given(messageFrame.getRemainingGas()).willReturn(Gas.of(300));

		given(mutableAccount.getStorageValue(any())).willReturn(keyBytes);
	}
}
