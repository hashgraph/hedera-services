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

package com.swirlds.platform.components.state;

import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.components.common.query.PrioritySystemTransactionSubmitter;
import com.swirlds.platform.components.state.output.IssConsumer;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.event.preconsensus.PreConsensusEventWriter;

/**
 * A factory capable of creating instances of {@link StateManagementComponent}.
 */
public interface StateManagementComponentFactory {

    /**
     * @param submitter
     * 		The {@link PrioritySystemTransactionSubmitter} to use for submitting new priority system transactions.
     * @return this
     */
    StateManagementComponentFactory prioritySystemTransactionConsumer(PrioritySystemTransactionSubmitter submitter);

    /**
     * @param consumer
     * 		The consumer to invoke when there is a state is written to disk or fails to write to disk
     * @return this
     */
    StateManagementComponentFactory stateToDiskConsumer(StateToDiskAttemptConsumer consumer);

    /**
     * @param consumer
     * 		The consumer to invoke when there is a new latest complete signed state
     * @return this
     */
    StateManagementComponentFactory newLatestCompleteStateConsumer(NewLatestCompleteStateConsumer consumer);

    /**
     * @param consumer
     * 		The consumer to invoke when a state is about to be ejected from memory without enough signatures
     * @return this
     */
    StateManagementComponentFactory stateLacksSignaturesConsumer(StateLacksSignaturesConsumer consumer);

    /**
     * @param consumer
     * 		The consumer to invoke when there a signed state gathers enough signatures to be complete for the first time
     * @return this
     */
    StateManagementComponentFactory newCompleteStateConsumer(StateHasEnoughSignaturesConsumer consumer);

    /**
     * @param consumer
     * 		The consumer to invoke when there is an ISS
     * @return this
     */
    StateManagementComponentFactory issConsumer(IssConsumer consumer);

    /**
     * @param consumer
     * 		The consumer to invoke when the system is requested to halt.
     * @return this
     */
    StateManagementComponentFactory haltRequestedConsumer(HaltRequestedConsumer consumer);

    /**
     * @param consumer
     * 		Any component that encounters a fatal error must invoke this consumer
     * @return this
     */
    StateManagementComponentFactory fatalErrorConsumer(FatalErrorConsumer consumer);

    /**
     * Set the preconsensus event writer.
     * @param preConsensusEventWriter the preconsensus event writer
     * @return this
     */
    StateManagementComponentFactory setPreConsensusEventWriter(PreConsensusEventWriter preConsensusEventWriter);

    /**
     * Builds a new {@link StateManagementComponent} with the provided inputs.
     *
     * @return the newly constructed {@link StateManagementComponent}
     * @throws IllegalStateException
     * 		if any required inputs are null
     */
    StateManagementComponent build();
}
