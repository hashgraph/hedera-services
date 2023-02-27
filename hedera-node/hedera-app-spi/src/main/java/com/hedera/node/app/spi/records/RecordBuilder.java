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

package com.hedera.node.app.spi.records;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * Tracks side-effects and metadata for a transaction. Is little more than a
 * marker interface for now, only tracking the final status of the transaction,
 * since this is all we need for https://github.com/hashgraph/hedera-services/issues/4945
 *
 * <p>A complete {@code RecordBuilder} would support tracking all the "universal"
 * side-effects and metadata of a transaction; and assembling these into a
 * {@code TransactionRecord} at the moment the transaction is being committed.
 *
 * <p>Examples of "universal" metadata include:
 * <ul>
 *     <li>The transaction id.</li>
 *     <li>The final status in the transaction receipt.</li>
 *     <li>The SHA-384 hash of the serialized {@code signedTransactionBytes}.</li>
 * </ul>
 * Examples of "universal" side-effects include:
 * <ul>
 *     <li>A paid staking rewards list.</li>
 *     <li>An hbar transfer list with at least fees and staking rewards.</li>
 * </ul>
 *
 * <p>Note that because all information being tracked by a {@code RecordBuilder} is
 * implicitly scoped to a <b>transaction that will accompany the final record in the
 * record stream</b>, any information already in the transaction does not need to be
 * repeated in the record. But some mild duplication is convenient and acceptable;
 * e.g., the transaction id is already in the transaction, but is repeated in the record.
 */
public interface RecordBuilder<T extends RecordBuilder<T>> {
    /**
     * Sets the final transaction status of this in-progress record.
     *
     * @param status the final status of the transaction
     * @return this builder
     */
    T setFinalStatus(ResponseCodeEnum status);

    /**
     * Returns the final transaction status of this in-progress record.
     *
     * @return the final status of the transaction
     * @throws IllegalStateException if the final status has already been set
     */
    ResponseCodeEnum getFinalStatus();
}
