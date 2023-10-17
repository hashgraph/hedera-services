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

package com.swirlds.platform.consensus;

public interface RoundNumberProvider {

    /**
     * return the round number below which the fame of all witnesses has been decided for all earlier rounds.
     *
     * @return the round number
     */
    long getFameDecidedBelow();

    /**
     * @return the latest round for which fame has been decided
     */
    default long getLastRoundDecided() {
        return getFameDecidedBelow() - 1;
    }

    /**
     * Return the max round number for which we have an event. If there are none yet, return {@link
     * ConsensusConstants#ROUND_UNDEFINED}.
     *
     * @return the max round number, or {@link ConsensusConstants#ROUND_UNDEFINED} if none.
     */
    long getMaxRound();

    /**
     * Return the minimum round number for which we have an event. If there are none yet, return
     * {@link ConsensusConstants#ROUND_UNDEFINED}.
     *
     * @return the minimum round number, or {@link ConsensusConstants#ROUND_UNDEFINED} if none.
     */
    long getMinRound();

    /**
     * Return the highest round number that has been deleted (or at least will be deleted soon).
     *
     * @return the round number that will be deleted (along with all earlier rounds)
     */
    default long getDeleteRound() {
        return getMinRound() - 1;
    }
}
