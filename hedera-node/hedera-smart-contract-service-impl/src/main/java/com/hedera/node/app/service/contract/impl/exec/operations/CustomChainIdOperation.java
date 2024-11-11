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

import static com.hedera.node.app.service.contract.impl.exec.operations.CustomizedOpcodes.CHAINID;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;

import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;

/**
 * A {@code CHAINID} operation that uses the {@link com.hedera.node.config.data.ContractsConfig} from the
 * frame context variables to provide the chain id. (The
 * {@link com.hedera.node.app.service.contract.impl.exec.TransactionProcessor} must always set
 * the config in the frame context, since in principle it could vary with every transaction.)
 */
public class CustomChainIdOperation extends AbstractOperation {
    private final long cost;

    /**
     * @param gasCalculator the gas calculator to use
     */
    public CustomChainIdOperation(@NonNull final GasCalculator gasCalculator) {
        super(CHAINID.opcode(), "CHAINID", 0, 1, gasCalculator);
        this.cost = Objects.requireNonNull(gasCalculator).getBaseTierGasCost();
    }

    @Override
    public OperationResult execute(@NonNull final MessageFrame frame, @NonNull final EVM evm) {
        if (frame.getRemainingGas() < cost) {
            return new OperationResult(cost, ExceptionalHaltReason.INSUFFICIENT_GAS);
        }
        final var chainIdAsInt =
                configOf(frame).getConfigData(ContractsConfig.class).chainId();
        final var chainId = Bytes32.fromHexStringLenient(Integer.toString(chainIdAsInt, 16));
        frame.pushStackItem(chainId);
        return new OperationResult(cost, null);
    }
}
