/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.util.records;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code StreamBuilder} that collects and builds the information required for atomic batch transaction.
 */
public interface AtomicBatchStreamBuilder extends StreamBuilder {

    /**
     * Tracks the synthetic transaction that dispatched the atomic batch transaction.
     * @param txn the synthetic transaction that represents the created system account
     * @return this builder
     */
    @NonNull
    AtomicBatchStreamBuilder transaction(@NonNull Transaction txn);

    /**
     * Tracks the synthetic transaction status.
     * @param status the status of the synthetic transaction
     * @return this builder
     */
    @NonNull
    AtomicBatchStreamBuilder status(@NonNull ResponseCodeEnum status);
}
