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
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A {@code StreamBuilder} specialization for tracking the side effects of {@link HederaFunctionality#ETHEREUM_TRANSACTION} transaction.
 */
public interface EthereumTransactionStreamBuilder extends ContractOperationStreamBuilder {
    /**
     * Tracks the final status of a HAPI Ethereum transaction.
     *
     * @param status the final status of the Ethereum transaction
     * @return this builder
     */
    @NonNull
    EthereumTransactionStreamBuilder status(@NonNull ResponseCodeEnum status);

    /**
     * Tracks the contract id called or created by the HAPI Ethereum transaction.
     *
     * @param contractId the {@link ContractID} called or created
     * @return this builder
     */
    @NonNull
    EthereumTransactionStreamBuilder contractID(@Nullable ContractID contractId);

    /**
     * Tracks the result of a HAPI Ethereum transaction performing a top-level contract call.
     *
     * @param result the {@link ContractFunctionResult} of the contract call
     * @return this builder
     */
    @NonNull
    EthereumTransactionStreamBuilder contractCallResult(@Nullable ContractFunctionResult result);

    /**
     * Tracks the result of a HAPI Ethereum transaction performing a top-level contract creation.
     *
     * @param result the {@link ContractFunctionResult} of the contract creation
     * @return this builder
     */
    @NonNull
    EthereumTransactionStreamBuilder contractCreateResult(@Nullable ContractFunctionResult result);

    /**
     * Tracks the hash of a HAPI Ethereum transaction.
     *
     * @param ethereumHash the {@link Bytes} of the Ethereum transaction hash
     * @return this builder
     */
    @NonNull
    EthereumTransactionStreamBuilder ethereumHash(@NonNull Bytes ethereumHash);
}
