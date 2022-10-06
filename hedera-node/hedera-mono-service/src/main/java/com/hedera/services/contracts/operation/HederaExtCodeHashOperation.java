/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiPredicate;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.ExtCodeHashOperation;

/**
 * Hedera adapted version of the {@link ExtCodeHashOperation}.
 *
 * <p>Performs an existence check on the requested {@link Address} Halts the execution of the EVM
 * transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if the account does
 * not exist or it is deleted.
 */
public class HederaExtCodeHashOperation extends ExtCodeHashOperation {

    private final BiPredicate<Address, MessageFrame> addressValidator;

    public HederaExtCodeHashOperation(
            GasCalculator gasCalculator, BiPredicate<Address, MessageFrame> addressValidator) {
        super(gasCalculator);
        this.addressValidator = addressValidator;
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        try {
            final Address address = Words.toAddress(frame.popStackItem());
            if (!addressValidator.test(address, frame)) {
                return new OperationResult(
                        OptionalLong.of(cost(true)),
                        Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS));
            }
            final var account = frame.getWorldUpdater().get(address);
            boolean accountIsWarm =
                    frame.warmUpAddress(address) || this.gasCalculator().isPrecompile(address);
            OptionalLong optionalCost = OptionalLong.of(this.cost(accountIsWarm));
            if (frame.getRemainingGas() < optionalCost.getAsLong()) {
                return new OperationResult(
                        optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
            } else {
                if (!account.isEmpty()) {
                    frame.pushStackItem(UInt256.fromBytes(account.getCodeHash()));
                } else {
                    frame.pushStackItem(UInt256.ZERO);
                }

                return new OperationResult(optionalCost, Optional.empty());
            }
        } catch (final FixedStack.UnderflowException ufe) {
            return new OperationResult(
                    OptionalLong.of(cost(true)),
                    Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
        }
    }
}
