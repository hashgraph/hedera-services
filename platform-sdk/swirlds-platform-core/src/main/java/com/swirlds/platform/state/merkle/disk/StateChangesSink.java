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

package com.swirlds.platform.state.merkle.disk;

import com.hedera.hapi.block.stream.output.StateChanges;

import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface StateChangesSink {

    void recordRoundStateChanges(@NonNull final Round round, @NonNull final Runnable fn);

    void recordEventStateChanges(@NonNull final ConsensusEvent platformEvent, @NonNull final Runnable fn);

    void recordSystemTransactionStateChanges(@NonNull final ConsensusTransaction platformTxn, @NonNull final Runnable fn);

    void recordUserTransactionStateChanges(@NonNull final ConsensusTransaction platformTxn, @NonNull final Runnable fn);

    void recordUserChildTransactionStateChanges(@NonNull final Runnable fn);

    /**
     * Write the state changes to the sink.
     * @param stateChanges the state changes to write
     */
    void writeStateChanges(@NonNull final StateChanges stateChanges);
}
