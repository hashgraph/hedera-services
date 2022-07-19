/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.operation;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.proto.SidecarType;
import com.hedera.services.utils.SidecarUtils;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;

public abstract class AbstractRecordingCreateOperation extends AbstractOperation {
    private static final int MAX_STACK_DEPTH = 1024;

    protected static final Operation.OperationResult INVALID_RESPONSE =
            new OperationResult(
                    OptionalLong.of(0L), Optional.of(ExceptionalHaltReason.INVALID_OPERATION));
    protected static final Operation.OperationResult UNDERFLOW_RESPONSE =
            new Operation.OperationResult(
                    OptionalLong.empty(),
                    Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));

    protected final GlobalDynamicProperties dynamicProperties;
    private final EntityCreator creator;
    private final SyntheticTxnFactory syntheticTxnFactory;
    private final RecordsHistorian recordsHistorian;

    protected AbstractRecordingCreateOperation(
            final int opcode,
            final String name,
            final int stackItemsConsumed,
            final int stackItemsProduced,
            final int opSize,
            final GasCalculator gasCalculator,
            final EntityCreator creator,
            final SyntheticTxnFactory syntheticTxnFactory,
            final RecordsHistorian recordsHistorian,
            final GlobalDynamicProperties dynamicProperties) {
        super(opcode, name, stackItemsConsumed, stackItemsProduced, opSize, gasCalculator);
        this.creator = creator;
        this.recordsHistorian = recordsHistorian;
        this.syntheticTxnFactory = syntheticTxnFactory;
        this.dynamicProperties = dynamicProperties;
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
        final OptionalLong optionalCost = OptionalLong.of(cost);
        if (frame.isStatic()) {
            return haltWith(optionalCost, ILLEGAL_STATE_CHANGE);
        } else if (frame.getRemainingGas() < cost) {
            return new Operation.OperationResult(
                    optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        }
        final Wei value = Wei.wrap(frame.getStackItem(0));

        final Address address = frame.getRecipientAddress();
        final MutableAccount account = frame.getWorldUpdater().getAccount(address).getMutable();

        frame.clearReturnData();

        if (value.compareTo(account.getBalance()) > 0
                || frame.getMessageStackDepth() >= MAX_STACK_DEPTH) {
            fail(frame);
        } else {
            spawnChildMessage(frame);
        }

        return new Operation.OperationResult(optionalCost, Optional.empty());
    }

    static Operation.OperationResult haltWith(
            final OptionalLong optionalCost, final ExceptionalHaltReason reason) {
        return new Operation.OperationResult(optionalCost, Optional.of(reason));
    }

    protected abstract boolean isEnabled();

    protected abstract long cost(final MessageFrame frame);

    protected abstract Address targetContractAddress(MessageFrame frame);

    private void fail(final MessageFrame frame) {
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
        final MutableAccount account = frame.getWorldUpdater().getAccount(address).getMutable();

        account.incrementNonce();

        final Wei value = Wei.wrap(frame.getStackItem(0));
        final long inputOffset = clampedToLong(frame.getStackItem(1));
        final long inputSize = clampedToLong(frame.getStackItem(2));
        final Bytes inputData = frame.readMemory(inputOffset, inputSize);

        final Address contractAddress = targetContractAddress(frame);

        final long childGasStipend =
                gasCalculator().gasAvailableForChildCreate(frame.getRemainingGas());
        frame.decrementRemainingGas(childGasStipend);

        final MessageFrame childFrame =
                MessageFrame.builder()
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
                        .code(Code.createLegacyCode(inputData, Hash.EMPTY))
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

    private void complete(final MessageFrame frame, final MessageFrame childFrame) {
        frame.setState(MessageFrame.State.CODE_EXECUTING);

        frame.incrementRemainingGas(childFrame.getRemainingGas());
        frame.addLogs(childFrame.getLogs());
        frame.addSelfDestructs(childFrame.getSelfDestructs());
        frame.incrementGasRefund(childFrame.getGasRefund());
        frame.popStackItems(getStackItemsConsumed());

        if (childFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
            frame.mergeWarmedUpFields(childFrame);
            frame.pushStackItem(Words.fromAddress(childFrame.getContractAddress()));

            // Add an in-progress record so that if everything succeeds, we can externalize the
            // newly
            // created contract in the record stream with both its 0.0.X id and its EVM address.
            // C.f. https://github.com/hashgraph/hedera-services/issues/2807
            final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
            final var sideEffects = new SideEffectsTracker();
            sideEffects.trackNewContract(
                    updater.idOfLastNewAddress(), childFrame.getContractAddress());
            final var childRecord =
                    creator.createSuccessfulSyntheticRecord(
                            NO_CUSTOM_FEES, sideEffects, EMPTY_MEMO);
            childRecord.onlyExternalizeIfSuccessful();
            final var opCustomizer = updater.customizerForPendingCreation();
            final var syntheticOp = syntheticTxnFactory.contractCreation(opCustomizer);
            if (dynamicProperties.enabledSidecars().contains(SidecarType.CONTRACT_BYTECODE)) {
                final var contractBytecodeSidecar =
                        SidecarUtils.createContractBytecodeSidecarFrom(
                                updater.idOfLastNewAddress(),
                                childFrame.getCode().getBytes().toArrayUnsafe(),
                                updater.get(childFrame.getContractAddress())
                                        .getCode()
                                        .toArrayUnsafe());
                updater.manageInProgressRecord(
                        recordsHistorian,
                        childRecord,
                        syntheticOp,
                        List.of(contractBytecodeSidecar));
            } else {
                updater.manageInProgressRecord(
                        recordsHistorian, childRecord, syntheticOp, Collections.emptyList());
            }
        } else {
            frame.setReturnData(childFrame.getOutputData());
            frame.pushStackItem(UInt256.ZERO);
        }

        final int currentPC = frame.getPC();
        frame.setPC(currentPC + 1);
    }
}
