package com.hedera.services.contracts.execution;

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


import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.services.state.enums.ContractActionType.CALL;
import static com.hedera.services.state.enums.ContractActionType.CREATE;
import static com.hedera.services.state.enums.ContractActionType.PRECOMPILE;
import static com.hedera.services.state.enums.ContractActionType.SYSTEM;

import com.hedera.services.state.enums.ContractActionType;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.SolidityAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Custom {@link OperationTracer} that populates exceptional halt reasons in the {@link MessageFrame}
 */
public class HederaTracer implements OperationTracer {

	private final PrecompileContractRegistry precompiledContractRegistry;
	private final List<SolidityAction> allActions;
	private final Deque<SolidityAction> currentActionsStack;

	public HederaTracer(final PrecompileContractRegistry precompileContractRegistry) {
		this.precompiledContractRegistry = precompileContractRegistry;
		this.currentActionsStack = new ArrayDeque<>();
		this.allActions = new ArrayList<>();
	}

	@Override
	public void traceExecution(MessageFrame frame, ExecuteOperation executeOperation) {
		if (currentActionsStack.isEmpty()) {
			trackAction(frame, 0);
		}

		executeOperation.execute();

		final var frameState = frame.getState();
		if (frameState != State.CODE_EXECUTING) {
			if (frameState == State.CODE_SUSPENDED) {
				final var nextFrame = frame.getMessageFrameStack().peek();
				if (nextFrame != frame) {
					trackAction(nextFrame, nextFrame.getMessageFrameStack().size() - 1);
				}
			} else {
				finalizeActionFor(frame, frameState);
			}
		}
	}

	@Override
	public void tracePrecompileCall(MessageFrame frame, long gasRequirement, Bytes output) {
		final var lastAction = currentActionsStack.pop();
		// specialize the call type - precompile or system (Hedera precompile contracts)
		lastAction.setCallType(
				precompiledContractRegistry.get(frame.getContractAddress()) != null ? PRECOMPILE : SYSTEM);
		// we have to null out recipient account and set recipient contract
		lastAction.setRecipientAccount(null);
		lastAction.setRecipientContract(EntityId.fromAddress(frame.getContractAddress()));
		finalizeActionFor(frame, frame.getState());
	}

	private void trackAction(final MessageFrame frame, final int callDepth) {
		// frame code can be empty when calling precompiles also, but we handle
		// that in tracePrecompileCall, after precompile execution is completed
		final var isCallToAccount = Code.EMPTY.equals(frame.getCode());
		final var isTopLevelEVMTransaction = callDepth == 0;
		final var action = new SolidityAction(
				toContractActionType(frame.getType()),
				isTopLevelEVMTransaction ? EntityId.fromAddress(frame.getOriginatorAddress()) : null,
				!isTopLevelEVMTransaction ? EntityId.fromAddress(frame.getSenderAddress()) : null,
				frame.getRemainingGas(),
				frame.getInputData().toArray(),
				isCallToAccount ? EntityId.fromAddress(frame.getContractAddress()) : null,
				!isCallToAccount ? EntityId.fromAddress(frame.getContractAddress()) : null,
				frame.getValue().toLong(),
				callDepth);
		allActions.add(action);
		currentActionsStack.push(action);
	}

	private void finalizeActionFor(MessageFrame frame, State frameState) {
		if (frameState == State.CODE_SUCCESS) {
			final var lastAction = currentActionsStack.pop();
			lastAction.setGasUsed(lastAction.getGas() - frame.getRemainingGas());
			// We do not set output data for create? Because it is externalized anyway in contract
			// bytecode sidecar?
			lastAction.setOutput(
					lastAction.getCallType() == CREATE ? new byte[0] : frame.getOutputData().toArrayUnsafe());
		} else if (frameState == State.REVERT) {
			final var lastAction = currentActionsStack.pop();
			// deliberate failures do not burn extra gas
			lastAction.setGasUsed(lastAction.getGas() - frame.getRemainingGas());
			// set the revert reason in the action
			lastAction.setRevertReason(frame.getRevertReason().orElse(Bytes.EMPTY).toArrayUnsafe());
		} else if (frameState == State.EXCEPTIONAL_HALT) {
			final var lastAction = currentActionsStack.pop();
			// exceptional exits always burn all gas
			lastAction.setGasUsed(lastAction.getGas());
			// exceptional halt state always has an exceptional halt reason set //TODO: true?
			final var exceptionalHaltReason = frame.getExceptionalHaltReason().get();
			// set the result as error
			lastAction.setError(exceptionalHaltReason.getDescription().getBytes(StandardCharsets.UTF_8));
			// if receiver was an invalid address, clear currently set receiver
			// and set invalid solidity address field
			if (exceptionalHaltReason.equals(INVALID_SOLIDITY_ADDRESS)) {
				if (lastAction.getRecipientAccount() != null) {
					lastAction.setInvalidSolidityAddress(lastAction.getRecipientAccount().toEvmAddress().toArrayUnsafe());
					lastAction.setRecipientAccount(null);
				} else {
					lastAction.setInvalidSolidityAddress(lastAction.getRecipientContract().toEvmAddress().toArrayUnsafe());
					lastAction.setRecipientContract(null);
				}
			}
		}
	}

	@Override
	public void traceAccountCreationResult(final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
		frame.setExceptionalHaltReason(haltReason);
	}

	private ContractActionType toContractActionType(final MessageFrame.Type type) {
		return switch (type) {
			case CONTRACT_CREATION -> CREATE;
			case MESSAGE_CALL -> CALL;
		};
	}

	public List<SolidityAction> getActions() {
		return allActions;
	}
}
