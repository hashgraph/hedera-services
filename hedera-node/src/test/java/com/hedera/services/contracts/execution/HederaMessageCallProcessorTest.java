package com.hedera.services.contracts.execution;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static com.hedera.services.contracts.execution.HederaMessageCallProcessor.INVALID_TRANSFER;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class HederaMessageCallProcessorTest {

	private static final String HEDERA_PRECOMPILE_ADDRESS_STRING = "0x1337";
	private static final Address HEDERA_PRECOMPILE_ADDRESS = Address.fromHexString(HEDERA_PRECOMPILE_ADDRESS_STRING);
	private static final Address RECIPIENT_ADDRESS = Address.fromHexString("0xcafecafe01");
	private static final Address SENDER_ADDRESS = Address.fromHexString("0xcafecafe02");
	private static final Gas GAS_ONE = Gas.of(1);
	private static final Gas GAS_ONE_K = Gas.of(1_000);
	private static final Gas GAS_ONE_M = Gas.of(1_000_000);

	@Mock
	EVM evm;
	@Mock
	PrecompileContractRegistry precompiles;
	@Mock
	PrecompiledContract hederaPrecompile;
	@Mock
	MessageFrame frame;
	@Mock
	OperationTracer operationTrace;

	@Mock
	WorldUpdater worldUpdater;

	HederaMessageCallProcessor subject;

	@BeforeEach
	void setup() {
		subject = new HederaMessageCallProcessor(evm, precompiles, Map.of(HEDERA_PRECOMPILE_ADDRESS_STRING, hederaPrecompile));
	}

	@Test
	void callsHederaPrecompile() {
		given(frame.getRemainingGas()).willReturn(Gas.of(1337));
		given(frame.getInputData()).willReturn(Bytes.EMPTY);
		given(frame.getContractAddress()).willReturn(HEDERA_PRECOMPILE_ADDRESS);
		given(hederaPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
		given(hederaPrecompile.compute(any(), eq(frame))).willReturn(Bytes.EMPTY);

		subject.start(frame, operationTrace);

		verify(hederaPrecompile).compute(Bytes.EMPTY, frame);
		verify(frame).getState();
		verify(operationTrace).tracePrecompileCall(frame, GAS_ONE, Bytes.EMPTY);
		verify(frame).decrementRemainingGas(GAS_ONE);
		verify(frame).setOutputData(Bytes.EMPTY);
		verify(frame).setState(COMPLETED_SUCCESS);
		verifyNoMoreInteractions(hederaPrecompile, frame, operationTrace);
	}

	@Test
	void callsParent() {
		given(frame.getWorldUpdater()).willReturn(worldUpdater);
		given(frame.getValue()).willReturn(Wei.ZERO);
		given(frame.getRecipientAddress()).willReturn(RECIPIENT_ADDRESS);
		given(frame.getSenderAddress()).willReturn(SENDER_ADDRESS);
		given(frame.getContractAddress()).willReturn(Address.fromHexString("0x1"));

		subject.start(frame, operationTrace);

		verify(frame).setState(MessageFrame.State.CODE_EXECUTING);
		verifyNoMoreInteractions(hederaPrecompile, frame, operationTrace);
	}

	@Test
	void insufficientGasReverts() {
		given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
		given(frame.getInputData()).willReturn(Bytes.EMPTY);
		given(hederaPrecompile.gasRequirement(any())).willReturn(GAS_ONE_M);

		subject.executeHederaPrecompile(hederaPrecompile, frame, operationTrace);

		verify(frame).setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
		verify(frame).setState(EXCEPTIONAL_HALT);
		verify(frame).decrementRemainingGas(GAS_ONE_K);
		verify(hederaPrecompile).compute(Bytes.EMPTY, frame);
		verify(frame).getState();
		verify(operationTrace).tracePrecompileCall(frame, GAS_ONE_M, null);
		verifyNoMoreInteractions(hederaPrecompile, frame, operationTrace);
	}

	@Test
	void precompileError() {
		given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
		given(frame.getInputData()).willReturn(Bytes.EMPTY);
		given(hederaPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
		given(hederaPrecompile.compute(any(), any())).willReturn(null);

		subject.executeHederaPrecompile(hederaPrecompile, frame, operationTrace);

		verify(frame).getState();
		verify(frame).setState(EXCEPTIONAL_HALT);
		verify(operationTrace).tracePrecompileCall(frame, GAS_ONE, null);
		verifyNoMoreInteractions(hederaPrecompile, frame, operationTrace);
	}
}
