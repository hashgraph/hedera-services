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


import com.google.common.primitives.Bytes;
import com.hedera.services.state.enums.ContractActionType;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.SolidityAction;
import com.hedera.services.utils.EntityIdUtils;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.plugin.data.Address;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.state.enums.ContractActionType.CALL;
import static com.hedera.services.state.enums.ContractActionType.CREATE;

/**
 * Custom {@link OperationTracer} that populates exceptional halt reasons in the {@link MessageFrame}
 */
public class HederaTracer implements OperationTracer {

	Deque<SolidityAction> actionStack = new ArrayDeque<>();
	List<SolidityAction> actions = new ArrayList<>();


	ContractActionType toContractActionType(MessageFrame.Type type) {
		switch (type) {
			case CONTRACT_CREATION:
				return CREATE;
			case MESSAGE_CALL:
				return CALL;
			default:
				return ContractActionType.NO_ACTION;
		}
	}

	@Override
	public void traceExecution(MessageFrame frame, ExecuteOperation executeOperation) {
		int callStackHeight = frame.getMessageFrameStack().size();
		int currentHeight = actionStack.size();

		//TODO: distinguish recipient contract from recipient account from invalid solidity address call
		if (currentHeight == 0) {
			var action = new SolidityAction(
					toContractActionType(frame.getType()),
					EntityId.fromAddress(frame.getOriginatorAddress()),
					null,
					frame.getRemainingGas(),
					frame.getInputData().toArray(),
					null,
					EntityId.fromAddress(frame.getContractAddress()),
					null,
					frame.getValue().toLong(),
					0,
					null,
					null,
					null,
					1);
			actions.add(action);
			actionStack.push(action);
		}

		executeOperation.execute();

		MessageFrame.State frameState = frame.getState();
		Optional<ExceptionalHaltReason> exceptionalHaltReason = frame.getExceptionalHaltReason();

		if (frameState == MessageFrame.State.CODE_SUSPENDED) {
			MessageFrame nextFrame = frame.getMessageFrameStack().peek();
			if (nextFrame != frame) {
				// create a new action
				var action = new SolidityAction(
						toContractActionType(nextFrame.getType()),
						null,
						EntityId.fromAddress(nextFrame.getSenderAddress()),
						nextFrame.getRemainingGas(),
						nextFrame.getInputData().toArray(),
						null,
						EntityId.fromAddress(nextFrame.getContractAddress()),
						null,
						nextFrame.getValue().toLong(),
						0,
						null,
						null,
						null,
						callStackHeight);
				actions.add(action);
				actionStack.push(action);
			}
		} else if (frameState == MessageFrame.State.CODE_SUCCESS) {
			// finish off old action
			var lastAction = actionStack.pop();
			lastAction.setGasUsed(lastAction.getGas() - frame.getRemainingGas());
//			lastAction.setSuccess(true);
			lastAction.setOutput(
					lastAction.getCallType() == CREATE ? new byte[0] : frame.getOutputData().toArrayUnsafe());
		} else if (frameState != MessageFrame.State.CODE_EXECUTING) {
			// failing exit
			var lastAction = actionStack.pop();
//			lastAction.setSuccess(false);
			if (exceptionalHaltReason.isPresent()) {
				// exceptional exits always burn all gas
				lastAction.setGasUsed(lastAction.getGas());
				lastAction.setError(
						exceptionalHaltReason.get().getDescription().getBytes(StandardCharsets.UTF_8));
			} else {
				// deliberate failures do not burn extra gas
				lastAction.setGasUsed(lastAction.getGas() - frame.getRemainingGas());
				if (frameState == MessageFrame.State.REVERT) {
//					lastAction.setRevertReason(frame.getRevertReason().map(Bytes::toArray).orElse(new byte[0]));
				}
			}
		}


	}

	@Override
	public void traceAccountCreationResult(
			final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
		frame.setExceptionalHaltReason(haltReason);
	}

	public List<SolidityAction> getActions() {
		return actions;
	}
}
