/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

/**
 * Invoked when a round has been completed. A round is considered to have been completed when all transactions from
 * that round have been fully handled.
 */
@FunctionalInterface
public interface RoundAppliedToStateConsumer {

    /**
     * Signal that a new round has been completed.
     *
     * @param round
     * 		the round number that was completed
     */
    void roundAppliedToState(long round);
}
