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

package com.hedera.services.bdd.suites.hip993;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder.ReversingBehavior;

/**
 * Asserts the expected presence and order of all valid combinations of preceding and following stream items;
 * both when rolled back and directly committed. It particularly emphasizes the natural ordering of
 * {@link TransactionCategory#PRECEDING} stream items as defined in
 * [HIP-993](<a href="https://github.com/hashgraph/hedera-improvement-proposal/blob/a64bdb258d52ba4ce1ca26bede8e03871b9ade10/HIP/hip-993.md#natural-ordering-of-preceding-records">...</a>).
 * <p>
 * The only stream items created in a savepoint that are <b>not</b> expected to be present are those with reversing
 * behavior {@link ReversingBehavior}, and whose originating savepoint was rolled back.
 * <p>
 * All other stream items are expected to be present in the record stream once created; but if they are
 * {@link ReversingBehavior#REVERSIBLE}, their status may be changed from {@link ResponseCodeEnum#SUCCESS} to
 * {@link ResponseCodeEnum#REVERTED_SUCCESS} when their originating savepoint is rolled back.
 * <p>
 * Only {@link ReversingBehavior#IRREVERSIBLE} streams items appear unchanged in the record stream no matter whether
 * their originating savepoint is rolled back.
 */
public class NaturalDispatchOrderingTest {}
