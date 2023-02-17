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

package com.hedera.node.app.service.contract.impl.test;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.contract.impl.components.ContractComponent;
import com.hedera.node.app.service.contract.impl.components.DaggerContractComponent;
import org.junit.jupiter.api.Test;

class ContractComponentTest {

    @Test
    void objectGraphRootsAreAvailable() {
        // given:
        ContractComponent subject = DaggerContractComponent.factory().create();

        // expect:
        assertNotNull(subject.contractCallHandler());
        assertNotNull(subject.contractCallLocalHandler());
        assertNotNull(subject.contractCreateHandler());
        assertNotNull(subject.contractDeleteHandler());
        assertNotNull(subject.contractGetBySolidityIDHandler());
        assertNotNull(subject.contractGetBytecodeHandler());
        assertNotNull(subject.contractGetInfoHandler());
        assertNotNull(subject.contractGetRecordsHandler());
        assertNotNull(subject.contractSystemDeleteHandler());
        assertNotNull(subject.contractSystemUndeleteHandler());
        assertNotNull(subject.contractUpdateHandler());
        assertNotNull(subject.etherumTransactionHandler());
    }
}
