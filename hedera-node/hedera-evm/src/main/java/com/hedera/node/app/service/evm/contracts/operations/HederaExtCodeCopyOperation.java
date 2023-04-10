/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.ExtCodeCopyOperation;

/**
 * Hedera adapted version of the {@link ExtCodeCopyOperation}.
 *
 * <p>Performs an existence check on the requested {@link Address} Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does
 * not exist or it is deleted.
 */
public class HederaExtCodeCopyOperation extends ExtCodeCopyOperation {

    private final BiPredicate<Address, MessageFrame> addressValidator;
    private final Predicate<Address> precompileDetector;

    public HederaExtCodeCopyOperation(
            GasCalculator gasCalculator, BiPredicate<Address, MessageFrame> addressValidator,
            Predicate<Address> precompileDetector) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
        this.precompileDetector = precompileDetector;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        final long memOffset = clampedToLong(frame.getStackItem(1));
        final long numBytes = clampedToLong(frame.getStackItem(3));
        final Supplier<OperationResult> operationResultSupplier = () -> {
            final var sourceOffset = clampedToLong(frame.getStackItem(2));
            frame.writeMemory(memOffset, sourceOffset, numBytes, Bytes.EMPTY);
            frame.popStackItems(4); // clear all the input arguments from the stack
            return new OperationResult(cost(frame, memOffset, numBytes, true), null);
        };
        return HederaEvmOperationsUtil.addressCheckExecution(
                frame,
                () -> frame.getStackItem(0),
                () -> cost(frame, memOffset, numBytes, true),
                () -> super.execute(frame, evm),
                addressValidator,
                precompileDetector,
                operationResultSupplier);
    }
}
