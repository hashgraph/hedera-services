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
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@ExtendWith(MockitoExtension.class)
class HederaOperationUtilTest {
	private static final Address PRETEND_RECIPIENT_ADDR = Address.ALTBN128_ADD;
	private static final Address PRETEND_CONTRACT_ADDR = Address.ALTBN128_MUL;
	private static final Address PRETEND_SENDER_ADDR = Address.ALTBN128_PAIRING;

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
	@Mock
	private Map<String, PrecompiledContract> precompiledContractMap;

	private final Optional<Gas> expectedHaltGas = Optional.of(Gas.of(10));
	private final Optional<Gas> expectedSuccessfulGas = Optional.of(Gas.of(100));

	@Test
	void shortCircuitsForPrecompileSigCheck() {
		final var degenerateResult =
				new Operation.OperationResult(Optional.empty(), Optional.empty());
		given(precompiledContractMap.containsKey(PRETEND_RECIPIENT_ADDR.toShortHexString())).willReturn(true);
		given(executionSupplier.get()).willReturn(degenerateResult);

		final var result = HederaOperationUtil.addressSignatureCheckExecution(
				sigsVerifier,
				messageFrame,
				PRETEND_RECIPIENT_ADDR,
				gasSupplier,
				executionSupplier,
				(a, b) -> false,
				precompiledContractMap);
		
		assertSame(degenerateResult, result);
	}

	@Test
	void computeExpiryForNewContractHappyPath() {
		final var expectedExpiry = 20L;

		Deque<MessageFrame> frameDeque = new ArrayDeque<>();
		frameDeque.add(messageFrame);

		given(messageFrame.getMessageFrameStack()).willReturn(frameDeque);
		given(messageFrame.getContextVariable("expiry")).willReturn(OptionalLong.of(expectedExpiry));

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
				executionSupplier,
				(a, b) -> true);

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
		given(gasSupplier.get()).willReturn(expectedHaltGas.get());

		// when:
		final var result = HederaOperationUtil.addressCheckExecution(
				messageFrame,
				() -> messageFrame.getStackItem(0),
				gasSupplier,
				executionSupplier,
				(a, b) -> false);

		// then:
		assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, result.getHaltReason().get());
		assertEquals(expectedHaltGas, result.getGasCost());
		// and:
		verify(messageFrame).getStackItem(0);
		verify(gasSupplier).get();
		verify(executionSupplier, never()).get();
	}

	@Test
	void successfulWhenAddressCheckExecution() {
		// given:
		given(messageFrame.getStackItem(0)).willReturn(Address.ZERO);
		given(executionSupplier.get())
				.willReturn(new Operation.OperationResult(expectedSuccessfulGas, Optional.empty()));

		// when:
		final var result = HederaOperationUtil.addressCheckExecution(
				messageFrame,
				() -> messageFrame.getStackItem(0),
				gasSupplier,
				executionSupplier,
				(a, b) -> true);

		// when:
		assertTrue(result.getHaltReason().isEmpty());
		assertEquals(expectedSuccessfulGas, result.getGasCost());
		// and:
		verify(messageFrame).getStackItem(0);
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
				executionSupplier,
				(a, b) -> false, precompiledContractMap);

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
		final var mockTarget = Address.ZERO;
		given(messageFrame.getRecipientAddress()).willReturn(Address.ALTBN128_ADD);
		given(messageFrame.getContractAddress()).willReturn(Address.ALTBN128_MUL);
		given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(hederaWorldUpdater.get(Address.ZERO)).willReturn(worldStateAccount);
		given(worldStateAccount.getAddress()).willReturn(Address.ZERO);
		given(sigsVerifier
				.hasActiveKeyOrNoReceiverSigReq(mockTarget, Address.ALTBN128_ADD, Address.ALTBN128_MUL, Address.ALTBN128_ADD))
				.willReturn(false);
		given(gasSupplier.get()).willReturn(expectedHaltGas.get());

		// when:
		final var result = HederaOperationUtil.addressSignatureCheckExecution(
				sigsVerifier,
				messageFrame,
				Address.ZERO,
				gasSupplier,
				executionSupplier,
				(a, b) -> true, precompiledContractMap);

		// then:
		assertEquals(HederaExceptionalHaltReason.INVALID_SIGNATURE, result.getHaltReason().get());
		assertEquals(expectedHaltGas, result.getGasCost());
		// and:
		verify(messageFrame).getWorldUpdater();
		verify(hederaWorldUpdater).get(Address.ZERO);
		verify(worldStateAccount).getAddress();
		verify(sigsVerifier).hasActiveKeyOrNoReceiverSigReq(mockTarget, PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR
				, PRETEND_RECIPIENT_ADDR);
		verify(gasSupplier).get();
		verify(executionSupplier, never()).get();
	}

	@Test
	void successfulWhenAddressSignatureCheckExecution() {
		// given:
		final var mockTarget = Address.ZERO;
		givenFrameAddresses();
		given(messageFrame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(hederaWorldUpdater.get(Address.ZERO)).willReturn(worldStateAccount);
		given(worldStateAccount.getAddress()).willReturn(Address.ZERO);
		given(sigsVerifier
				.hasActiveKeyOrNoReceiverSigReq(mockTarget, PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR, PRETEND_RECIPIENT_ADDR))
				.willReturn(true);
		given(executionSupplier.get())
				.willReturn(new Operation.OperationResult(expectedSuccessfulGas, Optional.empty()));

		// when:
		final var result = HederaOperationUtil.addressSignatureCheckExecution(
				sigsVerifier,
				messageFrame,
				Address.ZERO,
				gasSupplier,
				executionSupplier,
				(a, b) -> true, precompiledContractMap);

		// then:
		assertTrue(result.getHaltReason().isEmpty());
		assertEquals(expectedSuccessfulGas, result.getGasCost());
		// and:
		verify(messageFrame).getWorldUpdater();
		verify(hederaWorldUpdater).get(Address.ZERO);
		verify(worldStateAccount).getAddress();
		verify(sigsVerifier).hasActiveKeyOrNoReceiverSigReq(mockTarget, PRETEND_RECIPIENT_ADDR, PRETEND_CONTRACT_ADDR
				, PRETEND_RECIPIENT_ADDR);
		verify(gasSupplier, never()).get();
		verify(executionSupplier).get();
	}

	private void givenFrameAddresses() {
		given(messageFrame.getRecipientAddress()).willReturn(PRETEND_RECIPIENT_ADDR);
		given(messageFrame.getContractAddress()).willReturn(PRETEND_CONTRACT_ADDR);
	}
}
