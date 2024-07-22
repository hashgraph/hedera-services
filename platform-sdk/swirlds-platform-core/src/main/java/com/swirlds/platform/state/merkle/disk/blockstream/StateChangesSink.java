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

package com.swirlds.platform.state.merkle.disk.blockstream;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An interface responsible for recording the state changes on occurrence of particular events
 * (user, child and system transactions as well as end of an event and end of a round)
 */
public interface StateChangesSink {

    /**
     * Record end of round state changes
     * @param round the round we record the state changes
     * @param fn runnable to execute
     */
    void recordRoundStateChanges(@NonNull final Round round, @NonNull final Runnable fn);

    /**
     * Record event state changes
     * @param platformEvent the event we record the state changes
     * @param fn runnable to execute
     */
    void recordEventStateChanges(@NonNull final ConsensusEvent platformEvent, @NonNull final Runnable fn);

    /**
     * Record state changes from a system transaction
     * @param platformTxn the system transaction we record the state changes
     * @param fn runnable to execute
     */
    void recordSystemTransactionStateChanges(
            @NonNull final ConsensusTransaction platformTxn, @NonNull final Runnable fn);

    /**
     * Record state changes from a user transaction
     * @param platformTxn the user transaction we record the state changes
     * @param fn runnable to execute
     */
    void recordUserTransactionStateChanges(@NonNull final ConsensusTransaction platformTxn, @NonNull final Runnable fn);

    /**
     * Record state changes from a child transaction
     * @param fn runnable to execute
     */
    void recordUserChildTransactionStateChanges(@NonNull final Runnable fn);

    /**
     * Write the state changes to the sink.
     * @param stateChanges the state changes to write
     */
    void writeStateChanges(@NonNull final StateChanges stateChanges);
}
