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

package com.hedera.node.app.service.contract.impl.test.infra;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MOCK_CALL;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MOCK_CREATION;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MOCK_ETH;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.infra.HevmTransactionFactory;
import org.junit.jupiter.api.Test;

class HevmTransactionFactoryTest {
    private final HevmTransactionFactory subject = new HevmTransactionFactory();

    @Test
    void fromHapiTransactionThrowsOnNonContractOperation() {
        assertThrows(IllegalArgumentException.class, () -> subject.fromHapiTransaction(TransactionBody.DEFAULT));
    }

    @Test
    void fromHapiCreationNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.fromHapiTransaction(MOCK_CREATION));
    }

    @Test
    void fromHapiCallNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.fromHapiTransaction(MOCK_CALL));
    }

    @Test
    void fromHapiEthNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.fromHapiTransaction(MOCK_ETH));
    }
}
