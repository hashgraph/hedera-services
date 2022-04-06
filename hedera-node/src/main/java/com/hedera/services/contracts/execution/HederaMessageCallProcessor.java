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

import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
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
	private static final String INVALID_TRANSFER_MSG = "Transfer of Value to Hedera Precompile";
	public static final Bytes INVALID_TRANSFER = Bytes.of(INVALID_TRANSFER_MSG.getBytes(StandardCharsets.UTF_8));

	private final Map<Address, PrecompiledContract> hederaPrecompiles;

	public HederaMessageCallProcessor(
			final EVM evm,
			final PrecompileContractRegistry precompiles,
			final Map<String, PrecompiledContract> hederaPrecompileList
	) {
		super(evm, precompiles);
		hederaPrecompiles = new HashMap<>();
		hederaPrecompileList.forEach((k, v) -> hederaPrecompiles.put(Address.fromHexString(k), v));
	}

	// Create whitelist (0xhederanative)
	// Pass a create2 address related to 0xhederanative
	// 1. func(address, address)
	// 2. sender to be create2
	// sender, originator, contract, recipient have to always be resolved to a hedera native address

	// whitelist (accounts which can do stuff) : 0x0000000001 (correlates to 0xnativecreate2address)
	// mirrorAddress || evmAddressCreate2
	// {require(msg.sender == mirrorAddress), sender = create2 aliased address related to 0xnativeaddress => has to
	// pass}
	// contract storage (storage slot), field of type address
	// should not revert

	// investigate getAddress()


	// sender : 0x0000000001
	// require(msg.sender == 0xnativecreate2address)

	// 1. contract1 with map[address] for whitelist (isWhitelisted() bool - checks msg.sender, addToWhiteList(address))
	// 2. another contract2 which deploys another contract via create2 - contract3, has method checkWhiteList() {address(contract1).call(isWhiteListed())}
	// 3. contract1 addToWhiteList(mirrorAddress of the aliased contract from step 2)
	// 4. the aliased contract3 makes a call to the whitelisted contract1 isWhiteListed(). This has to be TRUE. Currently will return FALSE.
	// Step 4 needs to be executed both from HederaContractCall(with evmAddress) and through contract2 method(with mirrorAddress)

	@Override
	public void start(final MessageFrame frame, final OperationTracer operationTracer) {
		final var hederaPrecompile = hederaPrecompiles.get(frame.getContractAddress());
		if (hederaPrecompile != null) {
			executeHederaPrecompile(hederaPrecompile, frame, operationTracer);
		} else {
			super.start(frame, operationTracer);
		}
	}

	void executeHederaPrecompile(
			final PrecompiledContract contract,
			final MessageFrame frame,
			final OperationTracer operationTracer
	) {
		// EVM value transfers are not allowed
		if (!Objects.equals(Wei.ZERO, frame.getValue())) {
			frame.setRevertReason(INVALID_TRANSFER);
			frame.setState(REVERT);
			return;
		}

		final Gas gasRequirement;
		final Bytes output;
		if (contract instanceof HTSPrecompiledContract htsPrecompile) {
			final var costedResult = htsPrecompile.computeCosted(frame.getInputData(), frame);
			output = costedResult.getValue();
			gasRequirement = costedResult.getKey();
		} else {
			output = contract.compute(frame.getInputData(), frame);
			gasRequirement = contract.gasRequirement(frame.getInputData());
		}
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
