/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.hedera.node.app.hapi.utils.HederaExceptionalHaltReason;
import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiPredicate;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.OverflowException;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation;

public interface HederaEvmOperationsUtilV038 {
    public static String EVM_VERSION_0_46 = "v0.46";

    /**
     * An extracted address check and execution of extended Hedera Operations. Halts the execution
     * of the EVM transaction with {@link HederaExceptionalHaltReason#INVALID_SOLIDITY_ADDRESS} if
     * the account does not exist, or it is deleted.
     *
     * @param frame                        The current message frame
     * @param supplierAddressBytes         Supplier for the address bytes
     * @param supplierHaltGasCost          Supplier for the gas cost
     * @param supplierExecution            Supplier with the execution
     * @param addressValidator             Address validator predicate
     * @param systemAccountDetector  Hedera system account detector
     * @param systemAccountExecutionSupplier  Supplier for system account execution
     * @return The operation result of the execution
     */
    static Operation.OperationResult addressCheckExecution(
            @NonNull final MessageFrame frame,
            @NonNull final Supplier<Bytes> supplierAddressBytes,
            @NonNull final LongSupplier supplierHaltGasCost,
            @NonNull final Supplier<Operation.OperationResult> supplierExecution,
            @NonNull final BiPredicate<Address, MessageFrame> addressValidator,
            @NonNull final Predicate<Address> systemAccountDetector,
            @NonNull final Supplier<Operation.OperationResult> systemAccountExecutionSupplier,
            @NonNull final EvmProperties evmProperties) {
        try {
            final var address = Words.toAddress(supplierAddressBytes.get());
            if (systemAccountDetector.test(address)) {
                return systemAccountExecutionSupplier.get();
            }

            if (!evmProperties.callsToNonExistingEntitiesEnabled(frame.getContractAddress())) {
                if (Boolean.FALSE.equals(addressValidator.test(address, frame))) {
                    return new Operation.OperationResult(
                            supplierHaltGasCost.getAsLong(), HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS);
                }
            }

            return supplierExecution.get();
        } catch (final UnderflowException ufe) {
            return new Operation.OperationResult(
                    supplierHaltGasCost.getAsLong(), ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        } catch (final OverflowException ofe) {
            return new Operation.OperationResult(
                    supplierHaltGasCost.getAsLong(), ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
        }
    }
}
