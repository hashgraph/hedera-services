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

import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.utils.EntityNum;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HederaSelfDestructOperationTest {
	private static final EntityNum beneficiary = EntityNum.fromLong(2_345);
	private static final String ethAddress = "0xc257274276a4e539741ca11b590b9447b26a8051";
	private static final String anotherEthAddress = "0xc257274276a4e539741ca11b590b9447b26a8052";
	private static final Address eip1014Address = Address.fromHexString(ethAddress);
	private static final Address anotherEip1014Address = Address.fromHexString(anotherEthAddress);

	@Mock
	private HederaStackedWorldStateUpdater worldUpdater;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private MessageFrame frame;
	@Mock
	private EVM evm;
	@Mock
	private Account account;
	@Mock
	private BiPredicate<Address, MessageFrame> addressValidator;

	private HederaSelfDestructOperation subject;

	@BeforeEach
	void setUp() {
		subject = new HederaSelfDestructOperation(gasCalculator, addressValidator);

		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		given(gasCalculator.selfDestructOperationGasCost(any(), eq(Wei.ONE))).willReturn(Gas.of(2L));
	}

	@Test
	void delegatesToSuperWhenValid() {
		givenRubberstampValidator();

		final var beneficiaryMirror = beneficiary.toEvmAddress();
		given(frame.getStackItem(0)).willReturn(beneficiaryMirror);
		given(frame.popStackItem()).willReturn(beneficiaryMirror);
		given(worldUpdater.priorityAddress(beneficiaryMirror)).willReturn(beneficiaryMirror);
		given(frame.getRecipientAddress()).willReturn(eip1014Address);
		given(worldUpdater.get(any())).willReturn(account);
		given(account.getBalance()).willReturn(Wei.ONE);
		given(frame.isStatic()).willReturn(true);
		given(gasCalculator.getColdAccountAccessCost()).willReturn(Gas.of(1));

		final var opResult = subject.execute(frame, evm);

		assertEquals(Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE), opResult.getHaltReason());
		assertEquals(Optional.of(Gas.of(3L)), opResult.getGasCost());
	}

	@Test
	void rejectsSelfDestructToSelf() {
		givenRubberstampValidator();

		final var beneficiaryMirror = beneficiary.toEvmAddress();
		given(frame.getStackItem(0)).willReturn(beneficiaryMirror);
		given(worldUpdater.priorityAddress(beneficiaryMirror)).willReturn(eip1014Address);
		given(frame.getRecipientAddress()).willReturn(eip1014Address);

		final var opResult = subject.execute(frame, evm);

		assertEquals(Optional.of(HederaExceptionalHaltReason.SELF_DESTRUCT_TO_SELF), opResult.getHaltReason());
		assertEquals(Optional.of(Gas.of(2L)), opResult.getGasCost());
	}

	@Test
	void rejectsSelfDestructIfTreasury() {
		givenRubberstampValidator();

		final var beneficiaryMirror = beneficiary.toEvmAddress();
		given(frame.getStackItem(0)).willReturn(beneficiaryMirror);
		given(worldUpdater.priorityAddress(beneficiaryMirror)).willReturn(anotherEip1014Address);
		given(frame.getRecipientAddress()).willReturn(eip1014Address);
		given(worldUpdater.contractIsTokenTreasury(eip1014Address)).willReturn(true);

		final var opResult = subject.execute(frame, evm);

		assertEquals(Optional.of(HederaExceptionalHaltReason.CONTRACT_IS_TREASURY), opResult.getHaltReason());
		assertEquals(Optional.of(Gas.of(2L)), opResult.getGasCost());
	}

	@Test
	void rejectsSelfDestructIfContractHasAnyTokenBalance() {
		givenRubberstampValidator();

		final var beneficiaryMirror = beneficiary.toEvmAddress();
		given(frame.getStackItem(0)).willReturn(beneficiaryMirror);
		given(worldUpdater.priorityAddress(beneficiaryMirror)).willReturn(anotherEip1014Address);
		given(frame.getRecipientAddress()).willReturn(eip1014Address);
		given(worldUpdater.contractHasAnyBalance(eip1014Address)).willReturn(true);

		final var opResult = subject.execute(frame, evm);

		assertEquals(Optional.of(HederaExceptionalHaltReason.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES), opResult.getHaltReason());
		assertEquals(Optional.of(Gas.of(2L)), opResult.getGasCost());
	}

	@Test
	void rejectsSelfDestructIfContractHasAnyNfts() {
		givenRubberstampValidator();

		final var beneficiaryMirror = beneficiary.toEvmAddress();
		given(frame.getStackItem(0)).willReturn(beneficiaryMirror);
		given(worldUpdater.priorityAddress(beneficiaryMirror)).willReturn(anotherEip1014Address);
		given(frame.getRecipientAddress()).willReturn(eip1014Address);
		given(worldUpdater.contractOwnsNfts(eip1014Address)).willReturn(true);

		final var opResult = subject.execute(frame, evm);

		assertEquals(Optional.of(HederaExceptionalHaltReason.CONTRACT_STILL_OWNS_NFTS), opResult.getHaltReason());
		assertEquals(Optional.of(Gas.of(2L)), opResult.getGasCost());
	}

	@Test
	void executeInvalidSolidityAddress() {
		givenRejectingValidator();

		final var beneficiaryMirror = beneficiary.toEvmAddress();
		given(frame.getStackItem(0)).willReturn(beneficiaryMirror);
		given(worldUpdater.priorityAddress(beneficiaryMirror)).willReturn(eip1014Address);

		final var opResult = subject.execute(frame, evm);

		assertEquals(Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS), opResult.getHaltReason());
		assertEquals(Optional.of(Gas.of(2L)), opResult.getGasCost());
	}

	private void givenRubberstampValidator() {
		given(addressValidator.test(any(), any())).willReturn(true);
	}

	private void givenRejectingValidator() {
		given(addressValidator.test(any(), any())).willReturn(false);
	}
}
