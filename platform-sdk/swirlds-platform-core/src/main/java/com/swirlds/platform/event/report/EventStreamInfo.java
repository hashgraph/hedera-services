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

package com.swirlds.platform.event.report;

import com.swirlds.common.system.events.DetailedConsensusEvent;
import java.time.Instant;

/**
 * Information about an event stream.
 *
 * @param start
 * 		the timestamp at the start of the period reported
 * @param end
 * 		the timestamp at the end of the period reported
 * @param eventCount
 * 		the number of events in this period
 * @param transactionCount
 * 		the total number of transactions in this period
 * @param systemTransactionCount
 * 		the number of system transactions in this period
 * @param applicationTransactionCount
 * 		the number of application transactions in this period
 * @param fileCount
 * 		the number of files in this period
 * @param byteCount
 * 		the byte count of
 * @param firstEvent
 * 		the first event in the time period
 * @param lastEvent
 * 		the last event in the time period
 * @param damagedFileCount
 * 		the number of damaged files
 */
public record EventStreamInfo(
        Instant start,
        Instant end,
        long roundCount,
        long eventCount,
        long transactionCount,
        long systemTransactionCount,
        long applicationTransactionCount,
        long fileCount,
        long byteCount,
        DetailedConsensusEvent firstEvent,
        DetailedConsensusEvent lastEvent,
        long damagedFileCount) {}
