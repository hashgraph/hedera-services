/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.service.evm.contracts.operations;

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;

/**
 * Common logic for Hedera Create or Create2 operation.
 * <p>Externalizing child records for newly created contract as well as sidecar creation using a {@link CreateOperationExternalizer}
 */
public abstract class AbstractEvmRecordingCreateOperation extends AbstractOperation {
    protected static final int MAX_STACK_DEPTH = 1024;

    protected static final Operation.OperationResult INVALID_RESPONSE =
            new OperationResult(0L, ExceptionalHaltReason.INVALID_OPERATION);
    protected static final Operation.OperationResult UNDERFLOW_RESPONSE =
            new Operation.OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    private final CreateOperationExternalizer createOperationExternalizer;

    protected AbstractEvmRecordingCreateOperation(
            final int opcode,
            final String name,
            final int stackItemsConsumed,
            final int stackItemsProduced,
            final GasCalculator gasCalculator,
            final CreateOperationExternalizer createOperationExternalizer) {
        super(opcode, name, stackItemsConsumed, stackItemsProduced, gasCalculator);
        this.createOperationExternalizer = createOperationExternalizer;
    }

    @Override
    public Operation.OperationResult execute(final MessageFrame frame, final EVM evm) {
        // We have a feature flag for CREATE2
        if (!isEnabled()) {
            return INVALID_RESPONSE;
        }

        // manual check because some reads won't come until the "complete" step.
        if (frame.stackSize() < getStackItemsConsumed()) {
            return UNDERFLOW_RESPONSE;
        }

        final long cost = cost(frame);
        if (frame.isStatic()) {
            return haltWith(cost, ILLEGAL_STATE_CHANGE);
        } else if (frame.getRemainingGas() < cost) {
            return new Operation.OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
        }
        final Wei value = Wei.wrap(frame.getStackItem(0));

        final Address address = frame.getRecipientAddress();
        final MutableAccount account = frame.getWorldUpdater().getAccount(address);

        frame.clearReturnData();

        if (value.compareTo(account.getBalance()) > 0 || frame.getDepth() >= MAX_STACK_DEPTH) {
            fail(frame);
        } else {
            spawnChildMessage(frame);
        }

        return new Operation.OperationResult(cost, null);
    }

    public static Operation.OperationResult haltWith(final long cost, final ExceptionalHaltReason reason) {
        return new Operation.OperationResult(cost, reason);
    }

    protected abstract boolean isEnabled();

    protected abstract long cost(final MessageFrame frame);

    protected abstract Address targetContractAddress(MessageFrame frame);

    protected void fail(final MessageFrame frame) {
        final long inputOffset = clampedToLong(frame.getStackItem(1));
        final long inputSize = clampedToLong(frame.getStackItem(2));
        frame.readMutableMemory(inputOffset, inputSize);
        frame.popStackItems(getStackItemsConsumed());
        frame.pushStackItem(UInt256.ZERO);
    }

    private void spawnChildMessage(final MessageFrame frame) {
        // memory cost needs to be calculated prior to memory expansion
        final long cost = cost(frame);
        frame.decrementRemainingGas(cost);

        final Address address = frame.getRecipientAddress();
        final MutableAccount account = frame.getWorldUpdater().getAccount(address);

        account.incrementNonce();

        final Wei value = Wei.wrap(frame.getStackItem(0));
        final long inputOffset = clampedToLong(frame.getStackItem(1));
        final long inputSize = clampedToLong(frame.getStackItem(2));
        final Bytes inputData = frame.readMemory(inputOffset, inputSize);

        final Address contractAddress = targetContractAddress(frame);

        if (createOperationExternalizer.shouldFailBasedOnLazyCreation(frame, contractAddress)) {
            fail(frame);
            return;
        }

        final long childGasStipend = gasCalculator().gasAvailableForChildCreate(frame.getRemainingGas());
        frame.decrementRemainingGas(childGasStipend);

        // child frame is added to frame stack via build method
        MessageFrame.builder()
                .parentMessageFrame(frame)
                .type(MessageFrame.Type.CONTRACT_CREATION)
                .worldUpdater(frame.getWorldUpdater().updater())
                .initialGas(childGasStipend)
                .address(contractAddress)
                .originator(frame.getOriginatorAddress())
                .contract(contractAddress)
                .gasPrice(frame.getGasPrice())
                .inputData(Bytes.EMPTY)
                .sender(frame.getRecipientAddress())
                .value(value)
                .apparentValue(value)
                .code(CodeFactory.createCode(inputData, 0, false))
                .blockValues(frame.getBlockValues())
                .completer(child -> complete(frame, child))
                .miningBeneficiary(frame.getMiningBeneficiary())
                .blockHashLookup(frame.getBlockHashLookup())
                .maxStackSize(frame.getMaxStackSize())
                .build();

        frame.incrementRemainingGas(cost);

        frame.setState(MessageFrame.State.CODE_SUSPENDED);
    }

    private void complete(final MessageFrame frame, final MessageFrame childFrame) {
        frame.setState(MessageFrame.State.CODE_EXECUTING);

        frame.incrementRemainingGas(childFrame.getRemainingGas());
        frame.addLogs(childFrame.getLogs());
        frame.addSelfDestructs(childFrame.getSelfDestructs());
        frame.incrementGasRefund(childFrame.getGasRefund());
        frame.popStackItems(getStackItemsConsumed());

        if (childFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
            frame.pushStackItem(Words.fromAddress(childFrame.getContractAddress()));
            createOperationExternalizer.externalize(frame, childFrame);
        } else {
            frame.setReturnData(childFrame.getOutputData());
            frame.pushStackItem(UInt256.ZERO);
        }

        final int currentPC = frame.getPC();
        frame.setPC(currentPC + 1);
    }
}
