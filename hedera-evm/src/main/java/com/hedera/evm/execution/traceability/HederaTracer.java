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
package com.hedera.evm.execution.traceability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class HederaTracer implements HederaOperationTracer {
    private final List<SolidityAction> allActions;
    private final Deque<SolidityAction> currentActionsStack;
    private final boolean areActionSidecarsEnabled;

    public HederaTracer(final boolean areActionSidecarsEnabled) {
        this.currentActionsStack = new ArrayDeque<>();
        this.allActions = new ArrayList<>();
        this.areActionSidecarsEnabled = areActionSidecarsEnabled;
    }

    @Override
    public void init(final MessageFrame initialFrame) {
        //    if (areActionSidecarsEnabled) {
        //      // since this is the initial frame, call depth is always 0
        //      trackActionFor(initialFrame, 0);
        //    }
    }

    @Override
    public void tracePrecompileResult(MessageFrame frame, ContractActionType type) {}

    @Override
    public void traceExecution(MessageFrame frame, ExecuteOperation executeOperation) {}

    @Override
    public void tracePrecompileCall(MessageFrame frame, long gasRequirement, Bytes output) {
        HederaOperationTracer.super.tracePrecompileCall(frame, gasRequirement, output);
    }

    @Override
    public void traceAccountCreationResult(
            MessageFrame frame, Optional<ExceptionalHaltReason> haltReason) {
        HederaOperationTracer.super.traceAccountCreationResult(frame, haltReason);
    }

    public List<SolidityAction> getActions() {
        return allActions;
    }
}
