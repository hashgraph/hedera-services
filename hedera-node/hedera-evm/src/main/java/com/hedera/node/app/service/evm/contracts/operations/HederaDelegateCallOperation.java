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


import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.DelegateCallOperation;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;

/**
 * Hedera adapted version of the {@link DelegateCallOperation}.
 *
 * <p>Performs an existence check on the {@link Address} to be called Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does
 * not exist or it is deleted.
 */
public class HederaDelegateCallOperation extends DelegateCallOperation {

    private final BiPredicate<Address, MessageFrame> addressValidator;
    private final Predicate<Address> precompileDetector;
    private final PrecompileContractRegistry precompileContractRegistry;

    public HederaDelegateCallOperation(
            GasCalculator gasCalculator,
            BiPredicate<Address, MessageFrame> addressValidator,
            PrecompileContractRegistry precompileContractRegistry,
            Predicate<Address> precompileDetector) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
        this.precompileContractRegistry = precompileContractRegistry;
        this.precompileDetector = precompileDetector;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        final Supplier<OperationResult> precompileSupplierExecution = () -> {
            // we have an attempted call to a precompile address, but make sure an actual precompile exists at that address
            return precompileContractRegistry.get(to(frame)) != null
                    ? super.execute(frame, evm)
                    : new OperationResult(cost(frame), HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS);
        };
        return HederaEvmOperationsUtil.addressCheckExecution(
                frame,
                () -> to(frame),
                () -> cost(frame),
                () -> super.execute(frame, evm),
                addressValidator,
                precompileDetector,
                precompileSupplierExecution);
    }
}
