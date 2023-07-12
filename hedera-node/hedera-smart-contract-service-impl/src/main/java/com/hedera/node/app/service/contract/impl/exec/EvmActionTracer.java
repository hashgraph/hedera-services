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

package com.hedera.node.app.service.contract.impl.exec;

import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTracer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;

public class EvmActionTracer implements HederaEvmTracer {
    @Override
    public void customInit(@NonNull final MessageFrame frame) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void customFinalize(@NonNull final MessageFrame frame) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void tracePreExecution(@NonNull final MessageFrame frame) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void tracePostExecution(
            @NonNull final MessageFrame frame, @NonNull final Operation.OperationResult operationResult) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void tracePrecompileCall(
            @NonNull final MessageFrame frame, final long gasRequirement, @NonNull final Bytes output) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void traceAccountCreationResult(
            @NonNull final MessageFrame frame, @NonNull final Optional<ExceptionalHaltReason> haltReason) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void traceEndTransaction(@NonNull final Bytes output, final long gasUsed, final long timeNs) {
        throw new AssertionError("Not implemented");
    }
}
