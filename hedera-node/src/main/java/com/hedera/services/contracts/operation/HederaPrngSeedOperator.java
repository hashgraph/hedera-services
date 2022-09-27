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

import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.*;

import com.hedera.services.txns.util.PrngLogic;
import java.util.Optional;
import java.util.OptionalLong;
import javax.inject.Inject;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;

public class HederaPrngSeedOperator extends AbstractOperation {

    private final OperationResult successResponse;
    private final OperationResult oogResponse;
    private final PrngLogic prngLogic;

    private final long gasCost;

    @Inject
    public HederaPrngSeedOperator(PrngLogic prngLogic, GasCalculator gasCalculator) {
        super(0x44, "PRNGSEED", 0, 1, 1, gasCalculator);
        this.prngLogic = prngLogic;
        this.gasCost = gasCalculator.getBaseTierGasCost();
        OptionalLong optionalGasCost = OptionalLong.of(gasCost);
        this.successResponse = new OperationResult(optionalGasCost, Optional.empty());
        this.oogResponse = new OperationResult(optionalGasCost, Optional.of(INSUFFICIENT_GAS));
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        if (frame.getRemainingGas() < gasCost) {
            return oogResponse;
        }
        Bytes seed = Bytes.wrap(prngLogic.getNMinus3RunningHashBytes());
        if (seed.size() > Bytes32.SIZE) {
            frame.pushStackItem(seed.slice(0, Bytes32.SIZE));
        } else {
            frame.pushStackItem(seed);
        }
        return successResponse;
    }
}
