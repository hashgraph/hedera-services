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

import com.hedera.services.contracts.sources.SoliditySigsVerifier;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.utils.EntityIdUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class HederaOperationUtilTest {
	@Mock
	private MessageFrame messageFrame;

	@Mock
	private HederaWorldUpdater hederaWorldUpdater;

	@Mock
	private HederaWorldState.WorldStateAccount worldStateAccount;

	@Mock
	private SoliditySigsVerifier sigsVerifier;

	@Mock
	private Supplier<Gas> gasSupplier;

	@Mock
	private Supplier<Operation.OperationResult> executionSupplier;

	private final Optional<Gas> expectedHaltGas = Optional.of(Gas.of(10));
	private final Optional<Gas> expectedSuccessfulGas = Optional.of(Gas.of(100));


	@Test
	void computeExpiryForNewContractHappyPath() {
		final var expectedExpiry = 20L;

		Deque<MessageFrame> frameDeque = new ArrayDeque<>();
		frameDeque.add(messageFrame);

		given(messageFrame.getMessageFrameStack()).willReturn(frameDeque);
		given(messageFrame.getContextVariable("expiry")).willReturn(Optional.of(expectedExpiry));

		var actualExpiry = HederaOperationUtil.computeExpiryForNewContract(messageFrame);

		assertEquals(expectedExpiry, actualExpiry);
		verify(messageFrame).getMessageFrameStack();
		verify(messageFrame).getContextVariable("expiry");
	}

	@Test
	void computeExpiryForNewContractMultipleFrames() {
		final var expectedExpiry = 21L;

		Deque<MessageFrame> frameDeque = new ArrayDeque<>();
		frameDeque.add(messageFrame);
		frameDeque.add(messageFrame);

		final var customAddress = Address.fromHexString("0x0000000000001");

		given(messageFrame.getMessageFrameStack()).willReturn(frameDeque);
		given(messageFrame.getSenderAddress()).willReturn(customAddress);
		given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(hederaWorldUpdater.getHederaAccount(customAddress)).willReturn(worldStateAccount);
		given(worldStateAccount.getExpiry()).willReturn(expectedExpiry);

		var actualExpiry = HederaOperationUtil.computeExpiryForNewContract(messageFrame);

		assertEquals(expectedExpiry, actualExpiry);
		verify(messageFrame).getMessageFrameStack();
		verify(messageFrame).getSenderAddress();
		verify(hederaWorldUpdater).getHederaAccount(customAddress);
		verify(worldStateAccount).getExpiry();
		verify(messageFrame, never()).getContextVariable("expiry");
	}

	@Test
	void throwsUnderflowExceptionWhenGettingAddress() {
		// given:
		given(messageFrame.getStackItem(0)).willThrow(new FixedStack.UnderflowException());
		given(gasSupplier.get()).willReturn(expectedHaltGas.get());

		// when:
		final var result = HederaOperationUtil.addressCheckExecution(
				messageFrame,
				() -> messageFrame.getStackItem(0),
				gasSupplier,
				executionSupplier);

		// then:
		assertEquals(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS, result.getHaltReason().get());
		assertEquals(expectedHaltGas, result.getGasCost());
		// and:
		verify(messageFrame).getStackItem(0);
		verify(messageFrame, never()).getWorldUpdater();
		verify(gasSupplier).get();
		verify(executionSupplier, never()).get();
	}

	@Test
	void haltsWithInvalidSolidityAddressWhenAccountCheckExecution() {
		// given:
		given(messageFrame.getStackItem(0)).willReturn(Address.ZERO);
		given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(gasSupplier.get()).willReturn(expectedHaltGas.get());

		// when:
		final var result = HederaOperationUtil.addressCheckExecution(
				messageFrame,
				() -> messageFrame.getStackItem(0),
				gasSupplier,
				executionSupplier);

		// then:
		assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, result.getHaltReason().get());
		assertEquals(expectedHaltGas, result.getGasCost());
		// and:
		verify(messageFrame).getStackItem(0);
		verify(messageFrame).getWorldUpdater();
		verify(hederaWorldUpdater).get(Address.ZERO);
		verify(gasSupplier).get();
		verify(executionSupplier, never()).get();
	}

	@Test
	void successfulWhenAddressCheckExecution() {
		// given:
		given(messageFrame.getStackItem(0)).willReturn(Address.ZERO);
		given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(hederaWorldUpdater.get(Address.ZERO)).willReturn(worldStateAccount);
		given(executionSupplier.get())
				.willReturn(new Operation.OperationResult(expectedSuccessfulGas, Optional.empty()));

		// when:
		final var result = HederaOperationUtil.addressCheckExecution(
				messageFrame,
				() -> messageFrame.getStackItem(0),
				gasSupplier,
				executionSupplier);

		// when:
		assertTrue(result.getHaltReason().isEmpty());
		assertEquals(expectedSuccessfulGas, result.getGasCost());
		// and:
		verify(messageFrame).getStackItem(0);
		verify(messageFrame).getWorldUpdater();
		verify(hederaWorldUpdater).get(Address.ZERO);
		verify(gasSupplier, never()).get();
		verify(executionSupplier).get();
	}

	@Test
	void haltsWithInvalidSolidityAddressWhenAccountSignatureCheckExecution() {
		// given:
		given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(gasSupplier.get()).willReturn(expectedHaltGas.get());

		// when:
		final var result = HederaOperationUtil.addressSignatureCheckExecution(
				sigsVerifier,
				messageFrame,
				Address.ZERO,
				gasSupplier,
				executionSupplier);

		// then:
		assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, result.getHaltReason().get());
		assertEquals(expectedHaltGas, result.getGasCost());
		// and:
		verify(messageFrame).getWorldUpdater();
		verify(hederaWorldUpdater).get(Address.ZERO);
		verify(gasSupplier).get();
		verify(executionSupplier, never()).get();
	}

	@Test
	void haltsWithInvalidSignatureWhenAccountSignatureCheckExecution() {
		// given:
		final var mockSet = Set.of(EntityIdUtils.accountParsedFromSolidityAddress(Address.ZERO.toArray()));
		given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(hederaWorldUpdater.get(Address.ZERO)).willReturn(worldStateAccount);
		given(worldStateAccount.getAddress()).willReturn(Address.ZERO);
		given(sigsVerifier
				.allRequiredKeysAreActive(mockSet))
				.willReturn(false);
		given(gasSupplier.get()).willReturn(expectedHaltGas.get());

		// when:
		final var result = HederaOperationUtil.addressSignatureCheckExecution(
				sigsVerifier,
				messageFrame,
				Address.ZERO,
				gasSupplier,
				executionSupplier);

		// then:
		assertEquals(HederaExceptionalHaltReason.INVALID_SIGNATURE, result.getHaltReason().get());
		assertEquals(expectedHaltGas, result.getGasCost());
		// and:
		verify(messageFrame).getWorldUpdater();
		verify(hederaWorldUpdater).get(Address.ZERO);
		verify(worldStateAccount).getAddress();
		verify(sigsVerifier).allRequiredKeysAreActive(mockSet);
		verify(gasSupplier).get();
		verify(executionSupplier, never()).get();
	}

	@Test
	void successfulWhenAddressSignatureCheckExecution() {
		// given:
		final var mockSet = Set.of(EntityIdUtils.accountParsedFromSolidityAddress(Address.ZERO.toArray()));
		given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(hederaWorldUpdater.get(Address.ZERO)).willReturn(worldStateAccount);
		given(worldStateAccount.getAddress()).willReturn(Address.ZERO);
		given(sigsVerifier
				.allRequiredKeysAreActive(mockSet))
				.willReturn(true);
		given(executionSupplier.get())
				.willReturn(new Operation.OperationResult(expectedSuccessfulGas, Optional.empty()));

		// when:
		final var result = HederaOperationUtil.addressSignatureCheckExecution(
				sigsVerifier,
				messageFrame,
				Address.ZERO,
				gasSupplier,
				executionSupplier);

		// then:
		assertTrue(result.getHaltReason().isEmpty());
		assertEquals(expectedSuccessfulGas, result.getGasCost());
		// and:
		verify(messageFrame).getWorldUpdater();
		verify(hederaWorldUpdater).get(Address.ZERO);
		verify(worldStateAccount).getAddress();
		verify(sigsVerifier).allRequiredKeysAreActive(mockSet);
		verify(gasSupplier, never()).get();
		verify(executionSupplier).get();
	}
}
