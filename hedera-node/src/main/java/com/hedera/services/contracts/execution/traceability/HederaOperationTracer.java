/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.execution.traceability;

import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Hedera-specific EVM operation tracer interface with added functionality for contract actions
 * traceability.
 */
public interface HederaOperationTracer extends OperationTracer {

    /**
     * Trace the result from a precompile execution. Must be called after the result has been
     * reflected in the associated message frame.
     *
     * @param frame the frame associated with this precompile call
     * @param type the type of precompile called; expected values are {@code PRECOMPILE} and {@code SYSTEM}
     */
    void tracePrecompileResult(final MessageFrame frame, final ContractActionType type);

    /**
     * Obtain the list of contract actions created during current EVM transaction execution
     *
     * @return the list of contract actions created from this EVM transaction execution
     */
    List<SolidityAction> getActions();

    /**
     * Resets the tracer for a new EVM transaction. Must be called before each new EVM transaction
     * begins its execution.
     *
     * @param areActionSidecarsEnabled specifies if actions should be tracked for traceability
     *     during this EVM transaction
     */
    void reset(boolean areActionSidecarsEnabled);
}
