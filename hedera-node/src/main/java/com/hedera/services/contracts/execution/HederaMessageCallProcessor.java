package com.hedera.services.contracts.execution;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Overrides Besu precompiler handling so we can break model layers in Precompile execution
 */
public class HederaMessageCallProcessor extends MessageCallProcessor {

	Bytes INVALID_TRANSFER = Bytes.of("Transfer of Value to Hedera Precompile".getBytes(StandardCharsets.UTF_8));

	Map<Address, PrecompiledContract> hederaPrecompiles;

	public HederaMessageCallProcessor(final EVM evm, final PrecompileContractRegistry precompiles, Map<String, PrecompiledContract> hederaPrecompileList) {
		super(evm, precompiles);
		hederaPrecompiles = new HashMap<>();
		hederaPrecompileList.forEach((k, v) -> hederaPrecompiles.put(Address.fromHexString(k), v));
	}

	@Override
	public void start(final MessageFrame frame, final OperationTracer operationTracer) {
		var hederaPrecompile = hederaPrecompiles.get(frame.getContractAddress());
		if (hederaPrecompile != null) {
			// hedera precompile logic
			executePrecompile(hederaPrecompile, frame, operationTracer);
		} else {
			super.start(frame, operationTracer);
		}
	}

	private void executePrecompile(
			final PrecompiledContract contract,
			final MessageFrame frame,
			final OperationTracer operationTracer) {
		// EVM value transfers are not allowed
		if (!Objects.equals(Wei.ZERO, frame.getValue())) {
			frame.setRevertReason(INVALID_TRANSFER);
			frame.setState(MessageFrame.State.REVERT);
		}

		final Bytes output = contract.compute(frame.getInputData(), frame);
		final Gas gasRequirement = contract.gasRequirement(frame.getInputData());
		operationTracer.tracePrecompileCall(frame, gasRequirement, output);
		if (frame.getRemainingGas().compareTo(Gas.ZERO) < 0) {
			frame.setExceptionalHaltReason(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
			frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
		} else if (output != null) {
			frame.decrementRemainingGas(gasRequirement);
			frame.setOutputData(output);
			frame.setState(MessageFrame.State.COMPLETED_SUCCESS);
		} else {
			frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
		}
	}
}
