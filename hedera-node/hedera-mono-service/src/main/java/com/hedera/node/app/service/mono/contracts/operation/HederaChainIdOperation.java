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

package com.hedera.node.app.service.mono.contracts.operation;

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import javax.inject.Inject;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;

public class HederaChainIdOperation extends AbstractOperation {

    final GlobalDynamicProperties globalDynamicProperties;
    final Operation.OperationResult successResponse;
    private final OperationResult oogResponse;
    private final long gasCost;

    @Inject
    public HederaChainIdOperation(
            final GasCalculator gasCalculator, final GlobalDynamicProperties globalDynamicProperties) {
        super(0x46, "CHAINID", 0, 1, gasCalculator);
        this.globalDynamicProperties = globalDynamicProperties;
        this.gasCost = gasCalculator.getBaseTierGasCost();
        this.successResponse = new OperationResult(gasCost, null);
        this.oogResponse = new OperationResult(gasCost, INSUFFICIENT_GAS);
    }

    public Operation.OperationResult execute(final MessageFrame frame, final EVM evm) {
        if (frame.getRemainingGas() < gasCost) {
            return oogResponse;
        }
        frame.pushStackItem(globalDynamicProperties.chainIdBytes32());
        return successResponse;
    }
}
