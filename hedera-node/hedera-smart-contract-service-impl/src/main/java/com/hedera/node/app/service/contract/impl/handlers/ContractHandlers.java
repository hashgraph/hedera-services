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

package com.hedera.node.app.service.contract.impl.handlers;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ContractHandlers {

    private final ContractCallHandler contractCallHandler;

    private final ContractCallLocalHandler contractCallLocalHandler;

    private final ContractCreateHandler contractCreateHandler;

    private final ContractDeleteHandler contractDeleteHandler;

    private final ContractGetBySolidityIDHandler contractGetBySolidityIDHandler;

    private final ContractGetBytecodeHandler contractGetBytecodeHandler;

    private final ContractGetInfoHandler contractGetInfoHandler;

    private final ContractGetRecordsHandler contractGetRecordsHandler;

    private final ContractSystemDeleteHandler contractSystemDeleteHandler;

    private final ContractSystemUndeleteHandler contractSystemUndeleteHandler;

    private final ContractUpdateHandler contractUpdateHandler;

    private final EthereumTransactionHandler ethereumTransactionHandler;

    @Inject
    public ContractHandlers(
            @NonNull final ContractCallHandler contractCallHandler,
            @NonNull final ContractCallLocalHandler contractCallLocalHandler,
            @NonNull final ContractCreateHandler contractCreateHandler,
            @NonNull final ContractDeleteHandler contractDeleteHandler,
            @NonNull final ContractGetBySolidityIDHandler contractGetBySolidityIDHandler,
            @NonNull final ContractGetBytecodeHandler contractGetBytecodeHandler,
            @NonNull final ContractGetInfoHandler contractGetInfoHandler,
            @NonNull final ContractGetRecordsHandler contractGetRecordsHandler,
            @NonNull final ContractSystemDeleteHandler contractSystemDeleteHandler,
            @NonNull final ContractSystemUndeleteHandler contractSystemUndeleteHandler,
            @NonNull final ContractUpdateHandler contractUpdateHandler,
            @NonNull final EthereumTransactionHandler ethereumTransactionHandler) {
        this.contractCallHandler = requireNonNull(contractCallHandler, "contractCallHandler must not be null");
        this.contractCallLocalHandler =
                requireNonNull(contractCallLocalHandler, "contractCallLocalHandler must not be null");
        this.contractCreateHandler = requireNonNull(contractCreateHandler, "contractCreateHandler must not be null");
        this.contractDeleteHandler = requireNonNull(contractDeleteHandler, "contractDeleteHandler must not be null");
        this.contractGetBySolidityIDHandler =
                requireNonNull(contractGetBySolidityIDHandler, "contractGetBySolidityIDHandler must not be null");
        this.contractGetBytecodeHandler =
                requireNonNull(contractGetBytecodeHandler, "contractGetBytecodeHandler must not be null");
        this.contractGetInfoHandler = requireNonNull(contractGetInfoHandler, "contractGetInfoHandler must not be null");
        this.contractGetRecordsHandler =
                requireNonNull(contractGetRecordsHandler, "contractGetRecordsHandler must not be null");
        this.contractSystemDeleteHandler =
                requireNonNull(contractSystemDeleteHandler, "contractSystemDeleteHandler must not be null");
        this.contractSystemUndeleteHandler =
                requireNonNull(contractSystemUndeleteHandler, "contractSystemUndeleteHandler must not be null");
        this.contractUpdateHandler = requireNonNull(contractUpdateHandler, "contractUpdateHandler must not be null");
        this.ethereumTransactionHandler =
                requireNonNull(ethereumTransactionHandler, "ethereumTransactionHandler must not be null");
    }

    public ContractCallHandler contractCallHandler() {
        return contractCallHandler;
    }

    public ContractCallLocalHandler contractCallLocalHandler() {
        return contractCallLocalHandler;
    }

    public ContractCreateHandler contractCreateHandler() {
        return contractCreateHandler;
    }

    public ContractDeleteHandler contractDeleteHandler() {
        return contractDeleteHandler;
    }

    public ContractGetBySolidityIDHandler contractGetBySolidityIDHandler() {
        return contractGetBySolidityIDHandler;
    }

    public ContractGetBytecodeHandler contractGetBytecodeHandler() {
        return contractGetBytecodeHandler;
    }

    public ContractGetInfoHandler contractGetInfoHandler() {
        return contractGetInfoHandler;
    }

    public ContractGetRecordsHandler contractGetRecordsHandler() {
        return contractGetRecordsHandler;
    }

    public ContractSystemDeleteHandler contractSystemDeleteHandler() {
        return contractSystemDeleteHandler;
    }

    public ContractSystemUndeleteHandler contractSystemUndeleteHandler() {
        return contractSystemUndeleteHandler;
    }

    public ContractUpdateHandler contractUpdateHandler() {
        return contractUpdateHandler;
    }

    public EthereumTransactionHandler ethereumTransactionHandler() {
        return ethereumTransactionHandler;
    }
}
