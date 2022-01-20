package com.hedera.services.contracts.execution;

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
public class HederaMessageCallProcessorTest {

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
		given(frame.getValue()).willReturn(Wei.ZERO);
		given(frame.getInputData()).willReturn(Bytes.EMPTY);
		given(frame.getContractAddress()).willReturn(HEDERA_PRECOMPILE_ADDRESS);
		given(hederaPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
		given(hederaPrecompile.compute(any(), eq(frame))).willReturn(Bytes.EMPTY);

		subject.start(frame, operationTrace);

		verify(hederaPrecompile).compute(any(), eq(frame));
		verify(operationTrace).tracePrecompileCall(eq(frame), eq(GAS_ONE), eq(Bytes.EMPTY));
		verify(frame).decrementRemainingGas(eq(GAS_ONE));
		verify(frame).setOutputData(eq(Bytes.EMPTY));
		verify(frame).setState(eq(COMPLETED_SUCCESS));
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

		verify(frame).setState(eq(MessageFrame.State.CODE_EXECUTING));
		verifyNoMoreInteractions(hederaPrecompile, frame, operationTrace);
	}

	@Test
	void valueTransferNotAllowed() {
		given(frame.getValue()).willReturn(Wei.of(1));

		subject.executeHederaPrecompile(hederaPrecompile, frame, operationTrace);

		verify(frame).setRevertReason(eq(INVALID_TRANSFER));
		verify(frame).setState(eq(REVERT));
		verifyNoMoreInteractions(frame, hederaPrecompile);
	}

	@Test
	void insufficientGasReverts() {
		given(frame.getValue()).willReturn(Wei.ZERO);
		given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
		given(frame.getInputData()).willReturn(Bytes.EMPTY);
		given(hederaPrecompile.gasRequirement(any())).willReturn(GAS_ONE_M);

		subject.executeHederaPrecompile(hederaPrecompile, frame, operationTrace);

		verify(frame).setExceptionalHaltReason(eq(Optional.of(INSUFFICIENT_GAS)));
		verify(frame).setState(eq(EXCEPTIONAL_HALT));
		verify(frame).decrementRemainingGas(eq(GAS_ONE_K));
		verify(hederaPrecompile).compute(eq(Bytes.EMPTY), eq(frame));
		verify(operationTrace).tracePrecompileCall(eq(frame), eq(GAS_ONE_M), eq(null));
		verifyNoMoreInteractions(hederaPrecompile, frame, operationTrace);
	}

	@Test
	void precompileError() {
		given(frame.getValue()).willReturn(Wei.ZERO);
		given(frame.getRemainingGas()).willReturn(GAS_ONE_K);
		given(frame.getInputData()).willReturn(Bytes.EMPTY);
		given(hederaPrecompile.gasRequirement(any())).willReturn(GAS_ONE);
		given(hederaPrecompile.compute(any(), any())).willReturn(null);

		subject.executeHederaPrecompile(hederaPrecompile, frame, operationTrace);

		verify(frame).setState(eq(EXCEPTIONAL_HALT));
		verify(operationTrace).tracePrecompileCall(eq(frame), eq(GAS_ONE), eq(null));
		verifyNoMoreInteractions(hederaPrecompile, frame, operationTrace);
	}
}
