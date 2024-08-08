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

package com.hedera.node.app.service.networkadmin.impl;

import com.hedera.node.app.service.networkadmin.impl.handlers.FreezeHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetAccountDetailsHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetByKeyHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetExecutionTimeHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetVersionInfoHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkTransactionGetFastRecordHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkTransactionGetReceiptHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkTransactionGetRecordHandler;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkUncheckedSubmitHandler;
import dagger.Module;

/**
 * Dagger module for the networkadmin service.
 */
@Module
public interface NetworkAdminServiceInjectionModule {

    FreezeHandler freezeHandler();

    NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler();

    NetworkGetByKeyHandler networkGetByKeyHandler();

    NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler();

    NetworkGetVersionInfoHandler networkGetVersionInfoHandler();

    NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler();

    NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler();

    NetworkTransactionGetFastRecordHandler networkTransactionGetFastRecordHandler();

    NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler();
}
