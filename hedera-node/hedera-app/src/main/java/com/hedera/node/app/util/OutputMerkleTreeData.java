/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.util;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.swirlds.common.merkle.MerkleNode;

public class OutputMerkleTreeData {
    private MerkleNode outputRoot;
    private TransactionOutput transactionOutput;
    private TransactionResult transactionResult;
    private StateChanges stateChanges;

    // Constructor
    public OutputMerkleTreeData(
            MerkleNode outputRoot,
            TransactionOutput transactionOutput,
            TransactionResult transactionResult,
            StateChanges stateChanges) {
        this.outputRoot = outputRoot;
        this.transactionOutput = transactionOutput;
        this.transactionResult = transactionResult;
        this.stateChanges = stateChanges;
    }

    // Getters
    public MerkleNode getOutputRoot() {
        return outputRoot;
    }

    public TransactionOutput getTransactionOutput() {
        return transactionOutput;
    }

    public TransactionResult getTransactionResult() {
        return transactionResult;
    }

    public StateChanges getStateChanges() {
        return stateChanges;
    }
}
