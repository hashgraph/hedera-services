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
package com.hedera.services.contracts.execution;

import com.hedera.services.contracts.execution.traceability.HederaOperationTracer;
import com.hedera.services.contracts.execution.traceability.SolidityAction;
import com.hedera.services.contracts.execution.traceability.ContractActionType;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * A fake implementation of HederaOperationTracer for unit tests. This is needed since
 * EvmProcessor's tests need a tracer that executes the operations in traceExecution(frame,
 * operation), otherwise the tests hang.
 */
public class FakeHederaTracer implements HederaOperationTracer {

    private final List<SolidityAction> actions = new ArrayList<>();
    private boolean hasBeenReset;

    @Override
    public void traceExecution(MessageFrame frame, ExecuteOperation executeOperation) {
        executeOperation.execute();
    }

    @Override
    public void reset(boolean isActionTracingEnabled) {
        hasBeenReset = true;
    }

    public boolean hasBeenReset() {
        return hasBeenReset;
    }

    @Override
    public List<SolidityAction> getActions() {
        return actions;
    }

    public void addAction(final SolidityAction action) {
        this.actions.add(action);
    }

    @Override
    public void tracePrecompileResult(MessageFrame frame, ContractActionType type) {}
}
