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
package com.hedera.node.app.service.network.impl.components;

import com.hedera.node.app.service.network.impl.handlers.NetworkGetAccountDetailsHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetByKeyHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetExecutionTimeHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkGetVersionInfoHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkTransactionGetReceiptHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkTransactionGetRecordHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkUncheckedSubmitHandler;
import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component
public interface NetworkComponent {
    @Component.Factory
    interface Factory {
        NetworkComponent create();
    }

    NetworkGetAccountDetailsHandler networkGetAccountDetailsHandler();

    NetworkGetByKeyHandler networkGetByKeyHandler();

    NetworkGetExecutionTimeHandler networkGetExecutionTimeHandler();

    NetworkGetVersionInfoHandler networkGetVersionInfoHandler();

    NetworkTransactionGetReceiptHandler networkTransactionGetReceiptHandler();

    NetworkTransactionGetRecordHandler networkTransactionGetRecordHandler();

    NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler();
}
