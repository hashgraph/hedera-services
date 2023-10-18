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

/** Constants used for consensus */
public final class ConsensusConstants {
    private ConsensusConstants() {}

    /**
     * the consensus timestamp of a transaction is guaranteed to be at least this many nanoseconds
     * later than that of the transaction immediately before it in consensus order, and to be a
     * multiple of this (must be positive and a multiple of 10)
     */
    public static final long MIN_TRANS_TIMESTAMP_INCR_NANOS = 1_000;

    /** the number used to represent that a round has not been defined */
    public static final long ROUND_UNDEFINED = -1;
    /** represents a round of -infinity */
    public static final long ROUND_NEGATIVE_INFINITY = 0;
    /** the first round number */
    public static final long ROUND_FIRST = 1;
    /** value of the event mark when it is unmarked */
    public static final int EVENT_UNMARKED = 0;
}
