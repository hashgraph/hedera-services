/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.contract.impl;

import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallLocalHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetBySolidityIDHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetBytecodeHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetInfoHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractGetRecordsHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemUndeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.service.contract.impl.handlers.EtherumTransactionHandler;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link ContractService} {@link Service}.
 */
public final class ContractServiceImpl implements ContractService {

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

    private final EtherumTransactionHandler etherumTransactionHandler;

    public ContractServiceImpl() {
        this.contractCallHandler = new ContractCallHandler();
        this.contractCallLocalHandler = new ContractCallLocalHandler();
        this.contractCreateHandler = new ContractCreateHandler();
        this.contractDeleteHandler = new ContractDeleteHandler();
        this.contractGetBySolidityIDHandler = new ContractGetBySolidityIDHandler();
        this.contractGetBytecodeHandler = new ContractGetBytecodeHandler();
        this.contractGetInfoHandler = new ContractGetInfoHandler();
        this.contractGetRecordsHandler = new ContractGetRecordsHandler();
        this.contractSystemDeleteHandler = new ContractSystemDeleteHandler();
        this.contractSystemUndeleteHandler = new ContractSystemUndeleteHandler();
        this.contractUpdateHandler = new ContractUpdateHandler();
        this.etherumTransactionHandler = new EtherumTransactionHandler();
    }

    @NonNull
    public ContractCallHandler getContractCallHandler() {
        return contractCallHandler;
    }

    @NonNull
    public ContractCallLocalHandler getContractCallLocalHandler() {
        return contractCallLocalHandler;
    }

    @NonNull
    public ContractCreateHandler getContractCreateHandler() {
        return contractCreateHandler;
    }

    @NonNull
    public ContractDeleteHandler getContractDeleteHandler() {
        return contractDeleteHandler;
    }

    @NonNull
    public ContractGetBySolidityIDHandler getContractGetBySolidityIDHandler() {
        return contractGetBySolidityIDHandler;
    }

    @NonNull
    public ContractGetBytecodeHandler getContractGetBytecodeHandler() {
        return contractGetBytecodeHandler;
    }

    @NonNull
    public ContractGetInfoHandler getContractGetInfoHandler() {
        return contractGetInfoHandler;
    }

    @NonNull
    public ContractGetRecordsHandler getContractGetRecordsHandler() {
        return contractGetRecordsHandler;
    }

    @NonNull
    public ContractSystemDeleteHandler getContractSystemDeleteHandler() {
        return contractSystemDeleteHandler;
    }

    @NonNull
    public ContractSystemUndeleteHandler getContractSystemUndeleteHandler() {
        return contractSystemUndeleteHandler;
    }

    @NonNull
    public ContractUpdateHandler getContractUpdateHandler() {
        return contractUpdateHandler;
    }

    @NonNull
    public EtherumTransactionHandler getEtherumTransactionHandler() {
        return etherumTransactionHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
        return Set.of(
                contractCallHandler,
                contractCreateHandler,
                contractDeleteHandler,
                contractSystemDeleteHandler,
                contractSystemUndeleteHandler,
                contractUpdateHandler,
                etherumTransactionHandler
        );
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandler() {
        return Set.of(
                contractCallLocalHandler,
                contractGetBySolidityIDHandler,
                contractGetBytecodeHandler,
                contractGetInfoHandler,
                contractGetRecordsHandler
        );
    }
}
