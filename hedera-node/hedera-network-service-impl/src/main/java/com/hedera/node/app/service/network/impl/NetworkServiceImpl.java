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
package com.hedera.node.app.service.network.impl;

import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetAccountDetailsHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetByKeyHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetExecutionTimeHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetVersionInfoHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkTransactionGetReceiptHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkTransactionGetRecordHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkUncheckedSubmitHandler;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link NetworkService} {@link Service}.
 */
public final class NetworkServiceImpl implements NetworkService {

    private final NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler;

    private final NetworkGetByKeyHandler networkGetByKeyHandler;

    private final NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler;

    private final NetworkGetVersionInfoHandler networkGetVersionInfoHandler;

    private final NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler;

    private final NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler;

    private final NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler;

    public NetworkServiceImpl() {
        this.networkGetAccountDetailsHandler = new NetworkGetAccountDetailsHandler();
        this.networkGetByKeyHandler = new NetworkGetByKeyHandler();
        this.networkGetExecutionTimeHandler = new NetworkGetExecutionTimeHandler();
        this.networkGetVersionInfoHandler = new NetworkGetVersionInfoHandler();
        this.networkTransactionGetReceiptHandler = new NetworkTransactionGetReceiptHandler();
        this.networkTransactionGetRecordHandler = new NetworkTransactionGetRecordHandler();
        this.networkUncheckedSubmitHandler = new NetworkUncheckedSubmitHandler();
    }

    @NonNull
    public NetworkGetAccountDetailsHandler getNetworkGetAccountDetailsHandler() {
        return networkGetAccountDetailsHandler;
    }

    @NonNull
    public NetworkGetByKeyHandler getNetworkGetByKeyHandler() {
        return networkGetByKeyHandler;
    }

    @NonNull
    public NetworkGetExecutionTimeHandler getNetworkGetExecutionTimeHandler() {
        return networkGetExecutionTimeHandler;
    }

    @NonNull
    public NetworkGetVersionInfoHandler getNetworkGetVersionInfoHandler() {
        return networkGetVersionInfoHandler;
    }

    @NonNull
    public NetworkTransactionGetReceiptHandler getNetworkTransactionGetReceiptHandler() {
        return networkTransactionGetReceiptHandler;
    }

    @NonNull
    public NetworkTransactionGetRecordHandler getNetworkTransactionGetRecordHandler() {
        return networkTransactionGetRecordHandler;
    }

    @NonNull
    public NetworkUncheckedSubmitHandler getNetworkUncheckedSubmitHandler() {
        return networkUncheckedSubmitHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
        return Set.of(networkUncheckedSubmitHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandler() {
        return Set.of(
                networkGetAccountDetailsHandler,
                networkGetByKeyHandler,
                networkGetExecutionTimeHandler,
                networkGetVersionInfoHandler,
                networkTransactionGetReceiptHandler,
                networkTransactionGetRecordHandler);
    }
}
