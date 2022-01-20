package com.hedera.services.contracts.execution;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
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

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;

/**
 * Overrides Besu precompiler handling, so we can break model layers in Precompile execution
 */
public class HederaMessageCallProcessor extends MessageCallProcessor {

	static final Bytes INVALID_TRANSFER = Bytes.of("Transfer of Value to Hedera Precompile".getBytes(StandardCharsets.UTF_8));

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
			executeHederaPrecompile(hederaPrecompile, frame, operationTracer);
		} else {
			super.start(frame, operationTracer);
		}
	}

	void executeHederaPrecompile(
			final PrecompiledContract contract,
			final MessageFrame frame,
			final OperationTracer operationTracer) {
		// EVM value transfers are not allowed
		if (!Objects.equals(Wei.ZERO, frame.getValue())) {
			frame.setRevertReason(INVALID_TRANSFER);
			frame.setState(REVERT);
			return;
		}

		final Bytes output = contract.compute(frame.getInputData(), frame);
		final Gas gasRequirement = contract.gasRequirement(frame.getInputData());
		operationTracer.tracePrecompileCall(frame, gasRequirement, output);
		if (frame.getRemainingGas().compareTo(gasRequirement) < 0) {
			frame.decrementRemainingGas(frame.getRemainingGas());
			frame.setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
			frame.setState(EXCEPTIONAL_HALT);
		} else if (output != null) {
			frame.decrementRemainingGas(gasRequirement);
			frame.setOutputData(output);
			frame.setState(COMPLETED_SUCCESS);
		} else {
			frame.setState(EXCEPTIONAL_HALT);
		}
	}
}
