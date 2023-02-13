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
package com.hedera.node.app.service.contract.impl.components;

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
import dagger.Component;
import javax.inject.Singleton;

@Singleton
@Component
public interface ContractComponent {
    @Component.Factory
    interface Factory {
        ContractComponent create();
    }

    ContractCallHandler contractCallHandler();

    ContractCallLocalHandler contractCallLocalHandler();

    ContractCreateHandler contractCreateHandler();

    ContractDeleteHandler contractDeleteHandler();

    ContractGetBySolidityIDHandler contractGetBySolidityIDHandler();

    ContractGetBytecodeHandler contractGetBytecodeHandler();

    ContractGetInfoHandler contractGetInfoHandler();

    ContractGetRecordsHandler contractGetRecordsHandler();

    ContractSystemDeleteHandler contractSystemDeleteHandler();

    ContractSystemUndeleteHandler contractSystemUndeleteHandler();

    ContractUpdateHandler contractUpdateHandler();

    EtherumTransactionHandler etherumTransactionHandler();
}
