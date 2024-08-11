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

package com.hedera.node.app.workflows.standalone;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Executes a HAPI transaction within a "standalone" context based on a previously established {@link State}.
 */
@FunctionalInterface
public interface TransactionExecutor {
    /**
     * Executes a HAPI transaction within a "standalone" context based on a previously established {@link State} at
     * the given consensus time. If the transaction is a contract operation, the given Besu {@link OperationTracer}
     * instances will be given to the EVM. If it is not a contract operation, the operation tracers will be ignored.
     * <p>
     * Returns one or more {@link SingleTransactionRecord} instances from executing the transaction.
     * @param transactionBody the HAPI transaction body to execute
     * @param consensusNow the consensus time at which the transaction is to be executed
     * @param operationTracers the Besu {@link OperationTracer} instances to use for contract operations
     * @return one or more {@link SingleTransactionRecord}s for the executed transaction
     */
    List<SingleTransactionRecord> execute(
            @NonNull TransactionBody transactionBody,
            @NonNull Instant consensusNow,
            @NonNull OperationTracer... operationTracers);
}
