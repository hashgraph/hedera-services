/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.operations;

import static com.hedera.node.app.service.contract.impl.exec.operations.CustomizedOpcodes.PREVRANDAO;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;

/**
 * A Hedera replacement for {@link org.hyperledger.besu.evm.operation.PrevRanDaoOperation} that uses entropy from
 * the frame's {@link com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater}. (This is currently
 * implemented by surfacing the {@code N-3} record running hash for the current transaction.)
 */
public class CustomPrevRandaoOperation extends AbstractOperation {
    private final OperationResult successResult;
    private final OperationResult insufficientGasResult;

    private final long gasCost;

    /**
     * @param gasCalculator the gas calculator to be used
     */
    public CustomPrevRandaoOperation(@NonNull final GasCalculator gasCalculator) {
        super(PREVRANDAO.opcode(), "PRNGSEED", 0, 1, requireNonNull(gasCalculator));
        gasCost = gasCalculator.getBaseTierGasCost();
        successResult = new OperationResult(gasCost, null);
        insufficientGasResult = new OperationResult(gasCost, INSUFFICIENT_GAS);
    }

    @Override
    public OperationResult execute(@NonNull final MessageFrame frame, @NonNull final EVM evm) {
        requireNonNull(evm);
        requireNonNull(frame);
        if (frame.getRemainingGas() < gasCost) {
            return insufficientGasResult;
        }
        final var entropy = proxyUpdaterFor(frame).entropy();
        if (entropy.size() > Bytes32.SIZE) {
            frame.pushStackItem(entropy.slice(0, Bytes32.SIZE));
        } else {
            frame.pushStackItem(entropy);
        }
        return successResult;
    }
}
