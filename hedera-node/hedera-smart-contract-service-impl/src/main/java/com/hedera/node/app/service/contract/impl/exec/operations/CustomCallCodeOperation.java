/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.spi.meta.bni.Dispatch;
import com.hedera.node.app.spi.meta.bni.VerificationStrategy;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.CallCodeOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;

/**
 * A small customization of the Besu {@link CallCodeOperation} that checks for missing
 * addresses before delegating the call.
 *
 * <p><b>IMPORTANT:</b> This operation no longer does checks for receiver signature
 * requirements when value is being transferred; those requirements will be enforced in
 * the call the {@link MessageCallProcessor} makes to {@link Dispatch#transferWithReceiverSigCheck(long, long, long, VerificationStrategy)}.
 */
public class CustomCallCodeOperation extends CallCodeOperation {
    private static final Operation.OperationResult UNDERFLOW_RESPONSE =
            new Operation.OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
    private final AddressChecks addressChecks;

    public CustomCallCodeOperation(
            @NonNull final GasCalculator gasCalculator, @NonNull final AddressChecks addressChecks) {
        super(gasCalculator);
        this.addressChecks = addressChecks;
    }
}
