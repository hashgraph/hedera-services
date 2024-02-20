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

package com.hedera.node.app.service.contract.impl.records;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * Exposes the record customizations needed for a HAPI contract call transaction.
 */
public interface ContractCallRecordBuilder extends ContractOperationRecordBuilder {

    /**
     * Tracks the final status of a top-level contract call.
     *
     * @param status the final status of the contract call
     * @return this builder
     */
    @NonNull
    ContractCallRecordBuilder status(@NonNull ResponseCodeEnum status);

    /**
     * Returns final status of this contract call's record.
     *
     * @return the final status of this contract call
     */
    @NonNull
    ResponseCodeEnum status();

    /**
     * Tracks the contract id called.
     *
     * @param contractId the {@link ContractID} called
     * @return this builder
     */
    @NonNull
    ContractCallRecordBuilder contractID(@Nullable ContractID contractId);

    /**
     * Returns the token id created.
     *
     * @return the token id created
     */
    TokenID tokenID();

    /**
     * Tracks the result of a top-level contract call.
     *
     * @param result the {@link ContractFunctionResult} of the contract call
     * @return this builder
     */
    @NonNull
    ContractCallRecordBuilder contractCallResult(@Nullable ContractFunctionResult result);

    /**
     * Returns the in-progress {@link ContractFunctionResult}.
     *
     * @return the in-progress {@link ContractFunctionResult}
     */
    public ContractFunctionResult contractFunctionResult();

    /**
     * Tracks the transaction contained in child records resulting from the contract call.
     *
     * @param txn the transaction
     * @return this builder
     */
    @NonNull
    ContractCallRecordBuilder transaction(@NonNull final Transaction txn);

    /**
     * Gets the newly minted serial numbers.
     *
     * @return the newly minted serial numbers
     */
    List<Long> serialNumbers();

    /**
     * Gets the new total supply of a token, e.g. after minting or burning.
     *
     * @return new total supply of a token
     */
    long getNewTotalSupply();

    @NonNull
    ContractCallRecordBuilder entropyBytes(@NonNull final Bytes prngBytes);
}
