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

package com.hedera.node.app.service.contract.impl.handlers;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related handlers regarding contract operations in Hedera.
 */
public record ContractHandlers(
        @NonNull ContractCallHandler contractCallHandler,
        @NonNull ContractCallLocalHandler contractCallLocalHandler,
        @NonNull ContractCreateHandler contractCreateHandler,
        @NonNull ContractDeleteHandler contractDeleteHandler,
        @NonNull ContractGetBySolidityIDHandler contractGetBySolidityIDHandler,
        @NonNull ContractGetBytecodeHandler contractGetBytecodeHandler,
        @NonNull ContractGetInfoHandler contractGetInfoHandler,
        @NonNull ContractGetRecordsHandler contractGetRecordsHandler,
        @NonNull ContractSystemDeleteHandler contractSystemDeleteHandler,
        @NonNull ContractSystemUndeleteHandler contractSystemUndeleteHandler,
        @NonNull ContractUpdateHandler contractUpdateHandler,
        @NonNull EthereumTransactionHandler ethereumTransactionHandler,
        @NonNull LambdaSStoreHandler lambdaSStoreHandler,
        @NonNull LambdaDispatchHandler lambdaDispatchHandler) {
    public ContractHandlers {
        requireNonNull(contractCallHandler);
        requireNonNull(contractCallLocalHandler);
        requireNonNull(contractCreateHandler);
        requireNonNull(contractDeleteHandler);
        requireNonNull(contractGetBySolidityIDHandler);
        requireNonNull(contractGetBytecodeHandler);
        requireNonNull(contractGetInfoHandler);
        requireNonNull(contractGetRecordsHandler);
        requireNonNull(contractSystemDeleteHandler);
        requireNonNull(contractSystemUndeleteHandler);
        requireNonNull(contractUpdateHandler);
        requireNonNull(ethereumTransactionHandler);
        requireNonNull(lambdaSStoreHandler);
        requireNonNull(lambdaDispatchHandler);
    }
}
