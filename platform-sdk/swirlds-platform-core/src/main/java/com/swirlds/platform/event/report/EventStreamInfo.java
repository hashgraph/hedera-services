// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.event.report;

import com.swirlds.platform.system.events.CesEvent;
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
        CesEvent firstEvent,
        CesEvent lastEvent,
        long damagedFileCount) {}
