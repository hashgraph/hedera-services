/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.common.output;

import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Invoked when a new signed state has been created as a result of transaction processing.
 */
@FunctionalInterface
public interface NewSignedStateFromTransactionsConsumer {

    /**
     * A new signed state has been created. The state holds a single reservation. It is the responsibility of the
     * consumer to release the reservation when appropriate.
     *
     * @param stateAndRound the newly created signed state, with its associated round
     */
    void newSignedStateFromTransactions(@NonNull final StateAndRound stateAndRound);
}
