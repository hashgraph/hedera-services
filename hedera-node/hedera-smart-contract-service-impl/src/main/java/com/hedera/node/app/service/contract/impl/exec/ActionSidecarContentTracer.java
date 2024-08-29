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

package com.hedera.node.app.service.contract.impl.exec;

import com.hedera.hapi.streams.ContractActionType;
import com.hedera.hapi.streams.ContractActions;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * The Hedera-specific extensions to the {@link OperationTracer} interface we use to construct
 * and manage the {@link com.hedera.hapi.streams.ContractAction}'s in a sidecar of type
 * {@link com.hedera.hapi.streams.SidecarType#CONTRACT_ACTION}.
 */
public interface ActionSidecarContentTracer extends OperationTracer {
    /**
     * A hook we use to insert an action at the beginning of a transaction,
     * corresponding to the top-level HAPI operation.
     *
     * @param frame the initial frame of the just-beginning EVM transaction
     */
    void traceOriginAction(@NonNull MessageFrame frame);

    /**
     * A hook we use to "sanitize" any contract actions that have been
     * tracked during the transaction. Prevents invalid actions from
     * being exported to mirror nodes
     *
     * @param frame the initial frame of the just-finished EVM transaction
     */
    void sanitizeTracedActions(@NonNull MessageFrame frame);

    /**
     * A hook we use to manage the action sidecar of a precompile call result.
     *
     * @param frame the frame that called the precompile
     * @param type the type of precompile called; expected values are {@code PRECOMPILE} and {@code SYSTEM}
     */
    void tracePrecompileResult(@NonNull MessageFrame frame, @NonNull ContractActionType type);

    /**
     * The final list of actions traced by this tracer.
     *
     * @return the actions traced by this tracer
     */
    ContractActions contractActions();
}
