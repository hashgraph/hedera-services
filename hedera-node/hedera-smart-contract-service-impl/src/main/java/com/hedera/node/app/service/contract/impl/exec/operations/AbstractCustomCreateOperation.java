package com.hedera.node.app.service.contract.impl.exec.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.code.CodeFactory;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

public abstract class AbstractCustomCreateOperation extends AbstractOperation {
    private static final int MAX_STACK_DEPTH = 1024;
    private static final Operation.OperationResult INVALID_RESPONSE =
            new OperationResult(0L, ExceptionalHaltReason.INVALID_OPERATION);
    private static final Operation.OperationResult UNDERFLOW_RESPONSE =
            new Operation.OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

    protected AbstractCustomCreateOperation(
            final int opcode,
            @NonNull final String name,
            final int stackItemsConsumed,
            final int stackItemsProduced,
            @NonNull final GasCalculator gasCalculator) {
        super(opcode, name, stackItemsConsumed, stackItemsProduced, gasCalculator);
    }

    protected abstract boolean isEnabled(@NonNull MessageFrame frame);

    protected abstract long cost(@NonNull MessageFrame frame);

    protected abstract Address setupPendingCreation(@NonNull final MessageFrame frame);

    @Override
    public OperationResult execute(@NonNull final MessageFrame frame, @NonNull final EVM evm) {
        if (!isEnabled(frame)) {
            return INVALID_RESPONSE;
        }
        if (frame.stackSize() < getStackItemsConsumed()) {
            return UNDERFLOW_RESPONSE;
        }
        final long cost = cost(frame);
        if (frame.isStatic()) {
            return new OperationResult(cost, ILLEGAL_STATE_CHANGE);
        }
        if (frame.getRemainingGas() < cost) {
            return new Operation.OperationResult(cost, INSUFFICIENT_GAS);
        }
        final var value = Wei.wrap(frame.getStackItem(0));
        final var account = frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getMutable();
        frame.clearReturnData();
        if (value.compareTo(account.getBalance()) > 0 || frame.getMessageStackDepth() >= MAX_STACK_DEPTH) {
            fail(frame);
        } else {
            spawnChildMessage(frame);
        }
        return new Operation.OperationResult(cost, null);
    }

    private void fail(@NonNull final MessageFrame frame) {
        final var inputOffset = clampedToLong(frame.getStackItem(1));
        final var inputSize = clampedToLong(frame.getStackItem(2));
        frame.readMutableMemory(inputOffset, inputSize);
        frame.popStackItems(getStackItemsConsumed());
        frame.pushStackItem(UInt256.ZERO);
    }

    private void spawnChildMessage(@NonNull final MessageFrame frame) {
        // Calculate memory cost prior to expansion
        final var cost = cost(frame);
        frame.decrementRemainingGas(cost);

        final var account = frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getMutable();
        account.incrementNonce();

        final var value = Wei.wrap(frame.getStackItem(0));
        final var inputOffset = clampedToLong(frame.getStackItem(1));
        final var inputSize = clampedToLong(frame.getStackItem(2));
        final var inputData = frame.readMemory(inputOffset, inputSize);
        final var contractAddress = setupPendingCreation(frame);

        // TODO - fail if lazy creation without enabled flag
//        if (createOperationExternalizer.shouldFailBasedOnLazyCreation(frame, contractAddress)) {
//            fail(frame);
//            return;
//        }

        final var childGasStipend = gasCalculator().gasAvailableForChildCreate(frame.getRemainingGas());
        frame.decrementRemainingGas(childGasStipend);
        final var childFrame = MessageFrame.builder()
                .type(MessageFrame.Type.CONTRACT_CREATION)
                .messageFrameStack(frame.getMessageFrameStack())
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
                .depth(frame.getMessageStackDepth() + 1)
                .completer(child -> complete(frame, child))
                .miningBeneficiary(frame.getMiningBeneficiary())
                .blockHashLookup(frame.getBlockHashLookup())
                .maxStackSize(frame.getMaxStackSize())
                .build();
        frame.incrementRemainingGas(cost);
        frame.getMessageFrameStack().addFirst(childFrame);
        frame.setState(MessageFrame.State.CODE_SUSPENDED);
    }

    private void complete(@NonNull final MessageFrame frame, @NonNull final MessageFrame childFrame) {
        frame.setState(MessageFrame.State.CODE_EXECUTING);
        frame.incrementRemainingGas(childFrame.getRemainingGas());
        frame.addLogs(childFrame.getLogs());
        frame.addSelfDestructs(childFrame.getSelfDestructs());
        frame.incrementGasRefund(childFrame.getGasRefund());
        frame.popStackItems(getStackItemsConsumed());
        if (childFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
            frame.mergeWarmedUpFields(childFrame);
            frame.pushStackItem(Words.fromAddress(childFrame.getContractAddress()));
            // TODO - complete lazy creation if applicable
        } else {
            frame.setReturnData(childFrame.getOutputData());
            frame.pushStackItem(UInt256.ZERO);
        }
        final var currentPC = frame.getPC();
        frame.setPC(currentPC + 1);
    }
}