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

package com.hedera.node.app.service.network.impl.handlers;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NetworkHandlers {

    private final NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler;

    private final NetworkGetByKeyHandler networkGetByKeyHandler;

    private final NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler;

    private final NetworkGetVersionInfoHandler networkGetVersionInfoHandler;

    private final NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler;

    private final NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler;

    private final NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler;

    @Inject
    public NetworkHandlers(
            @NonNull final NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler,
            @NonNull final NetworkGetByKeyHandler networkGetByKeyHandler,
            @NonNull final NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler,
            @NonNull final NetworkGetVersionInfoHandler networkGetVersionInfoHandler,
            @NonNull final NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler,
            @NonNull final NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler,
            @NonNull final NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler) {
        this.networkGetAccountDetailsHandler =
                requireNonNull(networkGetAccountDetailsHandler, "networkGetAccountDetailsHandler must not be null");
        this.networkGetByKeyHandler = requireNonNull(networkGetByKeyHandler, "networkGetByKeyHandler must not be null");
        this.networkGetExecutionTimeHandler =
                requireNonNull(networkGetExecutionTimeHandler, "networkGetExecutionTimeHandler must not be null");
        this.networkGetVersionInfoHandler =
                requireNonNull(networkGetVersionInfoHandler, "networkGetVersionInfoHandler must not be null");
        this.networkTransactionGetReceiptHandler = requireNonNull(
                networkTransactionGetReceiptHandler, "networkTransactionGetReceiptHandler must not be null");
        this.networkTransactionGetRecordHandler = requireNonNull(
                networkTransactionGetRecordHandler, "networkTransactionGetRecordHandler must not be null");
        this.networkUncheckedSubmitHandler =
                requireNonNull(networkUncheckedSubmitHandler, "networkUncheckedSubmitHandler must not be null");
    }

    public NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler() {
        return networkGetAccountDetailsHandler;
    }

    public NetworkGetByKeyHandler networkGetByKeyHandler() {
        return networkGetByKeyHandler;
    }

    public NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler() {
        return networkGetExecutionTimeHandler;
    }

    public NetworkGetVersionInfoHandler networkGetVersionInfoHandler() {
        return networkGetVersionInfoHandler;
    }

    public NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler() {
        return networkTransactionGetReceiptHandler;
    }

    public NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler() {
        return networkTransactionGetRecordHandler;
    }

    public NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler() {
        return networkUncheckedSubmitHandler;
    }
}
