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
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.ContractKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Set;

/**
 * Standard implementation of the {@link ContractService}.
 */
public final class ContractServiceImpl implements ContractService {

    private static final int MAX_STORAGE_ENTRIES = 4096;

    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().setMinor(34).build();

    public static final String STORAGE_KEY = "STORAGE";

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

    /**
     * Default constructor.
     */
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

    /**
     * Returns the {@link ContractCallHandler} instance.
     *
     * @return the {@link ContractCallHandler} instance
     */
    @NonNull
    public ContractCallHandler getContractCallHandler() {
        return contractCallHandler;
    }

    /**
     * Returns the {@link ContractCallLocalHandler} instance.
     *
     * @return the {@link ContractCallLocalHandler} instance
     */
    @NonNull
    public ContractCallLocalHandler getContractCallLocalHandler() {
        return contractCallLocalHandler;
    }

    /**
     * Returns the {@link ContractCreateHandler} instance.
     *
     * @return the {@link ContractCreateHandler} instance
     */
    @NonNull
    public ContractCreateHandler getContractCreateHandler() {
        return contractCreateHandler;
    }

    /**
     * Returns the {@link ContractDeleteHandler} instance.
     *
     * @return the {@link ContractDeleteHandler} instance
     */
    @NonNull
    public ContractDeleteHandler getContractDeleteHandler() {
        return contractDeleteHandler;
    }

    /**
     * Returns the {@link ContractGetBySolidityIDHandler} instance.
     *
     * @return the {@link ContractGetBySolidityIDHandler} instance
     */
    @NonNull
    public ContractGetBySolidityIDHandler getContractGetBySolidityIDHandler() {
        return contractGetBySolidityIDHandler;
    }

    /**
     * Returns the {@link ContractGetBytecodeHandler} instance.
     *
     * @return the {@link ContractGetBytecodeHandler} instance
     */
    @NonNull
    public ContractGetBytecodeHandler getContractGetBytecodeHandler() {
        return contractGetBytecodeHandler;
    }

    /**
     * Returns the {@link ContractGetInfoHandler} instance.
     *
     * @return the {@link ContractGetInfoHandler} instance
     */
    @NonNull
    public ContractGetInfoHandler getContractGetInfoHandler() {
        return contractGetInfoHandler;
    }

    /**
     * Returns the {@link ContractGetRecordsHandler} instance.
     *
     * @return the {@link ContractGetRecordsHandler} instance
     */
    @NonNull
    public ContractGetRecordsHandler getContractGetRecordsHandler() {
        return contractGetRecordsHandler;
    }

    /**
     * Returns the {@link ContractSystemDeleteHandler} instance.
     *
     * @return the {@link ContractSystemDeleteHandler} instance
     */
    @NonNull
    public ContractSystemDeleteHandler getContractSystemDeleteHandler() {
        return contractSystemDeleteHandler;
    }

    /**
     * Returns the {@link ContractSystemUndeleteHandler} instance.
     *
     * @return the {@link ContractSystemUndeleteHandler} instance
     */
    @NonNull
    public ContractSystemUndeleteHandler getContractSystemUndeleteHandler() {
        return contractSystemUndeleteHandler;
    }

    /**
     * Returns the {@link ContractUpdateHandler} instance.
     *
     * @return the {@link ContractUpdateHandler} instance
     */
    @NonNull
    public ContractUpdateHandler getContractUpdateHandler() {
        return contractUpdateHandler;
    }

    /**
     * Returns the {@link EtherumTransactionHandler} instance.
     *
     * @return the {@link EtherumTransactionHandler} instance
     */
    @NonNull
    public EtherumTransactionHandler getEtherumTransactionHandler() {
        return etherumTransactionHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandlers() {
        return Set.of(
                contractCallHandler,
                contractCreateHandler,
                contractDeleteHandler,
                contractSystemDeleteHandler,
                contractSystemUndeleteHandler,
                contractUpdateHandler,
                etherumTransactionHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandlers() {
        return Set.of(
                contractCallLocalHandler,
                contractGetBySolidityIDHandler,
                contractGetBytecodeHandler,
                contractGetInfoHandler,
                contractGetRecordsHandler);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        Objects.requireNonNull(registry, "registry must not be null").register(contractSchema());
    }

    private Schema contractSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(storageDef());
            }
        };
    }

    private static StateDefinition<ContractKey, IterableContractValue> storageDef() {
        final var keySerdes = MonoMapSerdesAdapter.serdesForVirtualKey(
                ContractKey.MERKLE_VERSION, ContractKey::new, new ContractKeySerializer());
        final var valueSerdes = MonoMapSerdesAdapter.serdesForVirtualValue(
                IterableContractValue.ITERABLE_VERSION, IterableContractValue::new);

        return StateDefinition.onDisk(STORAGE_KEY, keySerdes, valueSerdes, MAX_STORAGE_ENTRIES);
    }
}
