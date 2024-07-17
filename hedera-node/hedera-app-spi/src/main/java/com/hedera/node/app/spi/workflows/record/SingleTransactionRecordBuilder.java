/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows.record;

/**
 * Defines API for constructing stream items of a single transaction dispatch.
 * The implementation may produce only records or could produce block items
 */
public interface SingleTransactionRecordBuilder extends SingleTransactionBuilder {
    /**
     * Sets the transactionID of the record based on the user transaction record.
     * @return the builder
     */
    SingleTransactionRecordBuilder syncBodyIdFromRecordId();
}
