/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.test.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.networkadmin.impl.handlers.FreezeHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkAdminHandlers;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetAccountDetailsHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetByKeyHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetExecutionTimeHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetVersionInfoHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkTransactionGetFastRecordHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkTransactionGetReceiptHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkTransactionGetRecordHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkUncheckedSubmitHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetworkAdminHandlersTest {
    private FreezeHandler freezeHandler;

    private NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler;

    private NetworkGetByKeyHandler networkGetByKeyHandler;

    private NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler;

    private NetworkGetVersionInfoHandler networkGetVersionInfoHandler;

    private NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler;

    private NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler;

    private NetworkTransactionGetFastRecordHandler networkTransactionGetFastRecordHandler;

    private NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler;

    private NetworkAdminHandlers networkAdminHandlers;

    @BeforeEach
    public void setUp() {
        freezeHandler = mock(FreezeHandler.class);
        networkGetAccountDetailsHandler = mock(NetworkGetAccountDetailsHandler.class);
        networkGetByKeyHandler = mock(NetworkGetByKeyHandler.class);
        networkGetExecutionTimeHandler = mock(NetworkGetExecutionTimeHandler.class);
        networkGetVersionInfoHandler = mock(NetworkGetVersionInfoHandler.class);
        networkTransactionGetReceiptHandler = mock(NetworkTransactionGetReceiptHandler.class);
        networkTransactionGetRecordHandler = mock(NetworkTransactionGetRecordHandler.class);
        networkTransactionGetFastRecordHandler = mock(NetworkTransactionGetFastRecordHandler.class);
        networkUncheckedSubmitHandler = mock(NetworkUncheckedSubmitHandler.class);

        networkAdminHandlers = new NetworkAdminHandlers(
                freezeHandler,
                networkGetAccountDetailsHandler,
                networkGetByKeyHandler,
                networkGetExecutionTimeHandler,
                networkGetVersionInfoHandler,
                networkTransactionGetReceiptHandler,
                networkTransactionGetRecordHandler,
                networkTransactionGetFastRecordHandler,
                networkUncheckedSubmitHandler);
    }

    @Test
    void freezeHandlerReturnsCorrectInstance() {
        assertEquals(
                freezeHandler, networkAdminHandlers.freezeHandler(), "freezeHandler does not return correct instance");
    }

    @Test
    void networkGetAccountDetailsHandlerReturnsCorrectInstance() {
        assertEquals(
                networkGetAccountDetailsHandler,
                networkAdminHandlers.networkGetAccountDetailsHandler(),
                "networkGetAccountDetailsHandler does not return correct instance");
    }

    @Test
    void networkGetByKeyHandlerReturnsCorrectInstance() {
        assertEquals(
                networkGetByKeyHandler,
                networkAdminHandlers.networkGetByKeyHandler(),
                "networkGetByKeyHandler does not return correct instance");
    }

    @Test
    void networkGetExecutionTimeHandlerReturnsCorrectInstance() {
        assertEquals(
                networkGetExecutionTimeHandler,
                networkAdminHandlers.networkGetExecutionTimeHandler(),
                "networkGetExecutionTimeHandler does not return correct instance");
    }

    @Test
    void networkGetVersionInfoHandlerReturnsCorrectInstance() {
        assertEquals(
                networkGetVersionInfoHandler,
                networkAdminHandlers.networkGetVersionInfoHandler(),
                "networkGetVersionInfoHandler does not return correct instance");
    }

    @Test
    void networkTransactionGetReceiptHandlerReturnsCorrectInstance() {
        assertEquals(
                networkTransactionGetReceiptHandler,
                networkAdminHandlers.networkTransactionGetReceiptHandler(),
                "networkTransactionGetReceiptHandler does not return correct instance");
    }

    @Test
    void networkTransactionGetRecordHandlerReturnsCorrectInstance() {
        assertEquals(
                networkTransactionGetRecordHandler,
                networkAdminHandlers.networkTransactionGetRecordHandler(),
                "networkTransactionGetRecordHandler does not return correct instance");
    }

    @Test
    void networkTransactionGetFastRecordHandlerReturnsCorrectInstance() {
        assertEquals(
                networkTransactionGetFastRecordHandler,
                networkAdminHandlers.networkTransactionGetFastRecordHandler(),
                "networkTransactionGetFastRecordHandler does not return correct instance");
    }

    @Test
    void networkUncheckedSubmitHandlerReturnsCorrectInstance() {
        assertEquals(
                networkUncheckedSubmitHandler,
                networkAdminHandlers.networkUncheckedSubmitHandler(),
                "networkUncheckSubmitHandler does not return correct instance");
    }
}
