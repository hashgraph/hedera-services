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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Exposes the record customizations needed for a HAPI contract create transaction.
 */
public interface ContractCreateStreamBuilder extends StreamBuilder, ContractOperationStreamBuilder {

    /**
     * Tracks the final status of a top-level contract creation.
     *
     * @param status the final status of the contract creation
     * @return this builder
     */
    @NonNull
    ContractCreateStreamBuilder status(@NonNull ResponseCodeEnum status);

    /**
     * Tracks the contract id created by a successful top-level contract creation.
     *
     * @param contractId the {@link ContractID} of the new top-level contract
     * @return this builder
     */
    @NonNull
    ContractCreateStreamBuilder contractID(@Nullable ContractID contractId);

    /**
     * Tracks the account id created by a successful top-level contract creation.
     * @param accountID the {@link AccountID} of the new top-level contract
     * @return this builder
     */
    @NonNull
    ContractCreateStreamBuilder accountID(@Nullable AccountID accountID);

    /**
     * Tracks the result of a top-level contract creation.
     *
     * @param result the {@link ContractFunctionResult} of the contract creation
     * @return this builder
     */
    @NonNull
    ContractCreateStreamBuilder contractCreateResult(@Nullable ContractFunctionResult result);

    @NonNull
    ContractCreateStreamBuilder transaction(@NonNull Transaction transaction);
}
